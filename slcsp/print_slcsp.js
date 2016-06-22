var Promises = require('bluebird'),
    _ = require("lodash"),
    fs = require("fs"),
    Converter = require('csvtojson').Converter,
    json2csv = Promises.promisify(require('json2csv')),
    opts = {checkType: false},
    slcsp = Promises.promisifyAll(new Converter(opts)).fromFileAsync('slcsp.csv'),
    plans = Promises.promisifyAll(new Converter(opts)).fromFileAsync('plans.csv'),
    zips = Promises.promisifyAll(new Converter(opts)).fromFileAsync('zips.csv')

/*
 * Modify the original files in the following ways
 * Plans - filter out non silver and then group by rate area
 * Zips - build map where the zips are keys and the rate area is the value
 */

var zipToAreaMap = zips.then(buildZipHash)
var plansGroupedByArea = plans.filter(isSilverPlan).then(groupPlans)

Promises.join(slcsp, plansGroupedByArea, zipToAreaMap, calculateSLSCP)
  .then(writeSLSCPFile)
  .then(function() {process.exit(0)})
  .catch(function(err) {
    console.dir(err)
    process.exit(1)
  })

function calculateSLSCP(slcsp, plans, zips) {
  // For each row grab the area from the zip map, sort plans in that area
  _.each(slcsp, function(row) {
    var rateArea = zips[row.zipcode],
        plansInArea = _.sortBy(plans[rateArea] || [], 'rate')

    if (plansInArea.length < 2) return;
    row.rate = plansInArea.length < 2 ? undefined : plansInArea[1].rate
  })

  return slcsp
}

function writeSLSCPFile(rows) {
  return json2csv({data: rows, fields: ['zipcode', 'rate']})
    .then(_.partial(fs.writeFileSync, 'slscp.csv'))
}

function isSilverPlan(plan) { return plan.metal_level === 'Silver' }

function buildZipHash(zips) {
  return _.transform(zips, function(memo, info) {
    memo[info.zipcode] = buildUniqueRateArea(info)
  }, {})
}

function groupPlans(plans) {
  return _.groupBy(plans, buildUniqueRateArea)
}

function buildUniqueRateArea(info) {
  return [info.state, info.rate_area].join(" ")
}

