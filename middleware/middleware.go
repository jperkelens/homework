package main

import (
	"crypto/sha1"
	"encoding/hex"
	"flag"
	"fmt"
	"io"
	"io/ioutil"
	"log"
	"net/http"
	"net/http/httptest"
	"sort"
	"strconv"
	"strings"
)

const (
	crlf       = "\r\n"
	colonspace = ": "
)

func ChecksumMiddleware(h http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Record response
		res := httptest.NewRecorder()
		h.ServeHTTP(res, r)
		hash := sha1.New()

		// Read headers + body
		var headerKeys = []string{}
		var headers = res.Header()
		var body, _ = ioutil.ReadAll(res.Body)

		// Sort Headers
		for k, _ := range headers {
			headerKeys = append(headerKeys, k)
		}
		sort.Strings(headerKeys)

		// Add Code
		io.WriteString(hash, fmt.Sprintf("%v", res.Code)+crlf)

		// Append Headers
		for _, header := range headerKeys {
			io.WriteString(hash, header+colonspace+
				strings.Join(headers[header], "")+crlf)
		}

		io.WriteString(hash, "X-Checksum-Headers: "+
			strings.Join(headerKeys, ";")+crlf+crlf+string(body))

		// Add Checksum header
		checksum := hash.Sum(nil)
		w.Header().Set("X-Checksum", hex.EncodeToString(checksum))

		h.ServeHTTP(w, r)
	})
}

// Do not change this function.
func main() {
	var listenAddr = flag.String("http", ":8080", "address to listen on for HTTP")
	flag.Parse()

	http.Handle("/", ChecksumMiddleware(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Foo", "bar")
		w.Header().Set("Content-Type", "text/plain")
		w.Header().Set("Date", "Sun, 08 May 2016 14:04:53 GMT")
		msg := "Curiosity is insubordination in its purest form.\n"
		w.Header().Set("Content-Length", strconv.Itoa(len(msg)))
		fmt.Fprintf(w, msg)
	})))

	log.Fatal(http.ListenAndServe(*listenAddr, nil))
}
