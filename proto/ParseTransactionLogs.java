import java.lang.System;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.math.BigInteger;

public class ParseTransactionLogs {
  public static final BigInteger USER_OF_INTEREST = new BigInteger("2456938384156277127");

  //Enum for transaction types
  public enum Type { 
    DEBIT(0), CREDIT(1), START_AUTOPAY(2), END_AUTOPAY(3);
    private final int value;

    Type(int value) {
      this.value = value;
    }

    public int getValue() {
      return value;
    }
  };

  // Main method, push file into a ByteBuffer, 
  // parse the header then parse the transactions
  public static void main(String[] args) {
    try {
      Path path = Paths.get("txnlog.dat");
      ByteBuffer transLog = ByteBuffer.wrap(Files.readAllBytes(path));
      ArrayList<Transaction> transactions = new ArrayList<Transaction>();

      LogInfo info = new LogInfo(transLog);
      for (int i = 0; i < info.transactionNumber; i++) {
        transactions.add(new Transaction(transLog));
      }
      printStats(transactions);
    } catch (Exception e) {
      System.out.println("File cannot be read or is malformed");
    }
  }

  // Helper method to retrieve an unsigned int and store it in a long
  public static long getUInt(ByteBuffer buf) {
    byte[] longArr = new byte[8];
    byte[] significantArr = new byte[4];
    buf.get(significantArr);
    for (int i = 0; i < 4; i++) {
      longArr[i+4] = significantArr[i];
    }

    return ByteBuffer.wrap(longArr).getLong();
  }

  // Count all transaction types, sum credits and debits and 
  // calculate balance of the user of interest
  public static void printStats(ArrayList<Transaction> transactions) {
    int[] typeCounts = new int[4];
    double[] totalBalances = new double[2];
    double userBalance = 0;

    for (Transaction currTransaction : transactions) {
      typeCounts[currTransaction.type.getValue() ]++;

      if (currTransaction.hasAmount())
        totalBalances[currTransaction.type.getValue()] += currTransaction.amount;

      if (currTransaction.userId.equals(USER_OF_INTEREST))
        userBalance += currTransaction.type == Type.DEBIT ?
          -currTransaction.amount 
          : currTransaction.amount;
    }

    System.out.println("Total Debit Amounts: " + totalBalances[Type.DEBIT.getValue()]);
    System.out.println("Total Credit Amounts: " + totalBalances[Type.CREDIT.getValue()]);
    System.out.println("Total Start Autopays: " + typeCounts[Type.START_AUTOPAY.getValue()]);
    System.out.println("Total End Autopays: " + typeCounts[Type.END_AUTOPAY.getValue()]);
    System.out.println("Total balance for user " + USER_OF_INTEREST +  ": " + userBalance);
  }

  // Class that parses and validates the log header
  // Throws an exception if it does not start with the magic string
  static class LogInfo {
    public byte version;
    public long transactionNumber;
    final String LOG_PREFIX = "MPS7";

    public LogInfo(ByteBuffer buf) throws Exception {
      byte[] stringBytes = new byte[4];
      buf.get(stringBytes);

      String magicString = new String(stringBytes, StandardCharsets.UTF_8);
      if (!magicString.equals(LOG_PREFIX)) throw new Exception();

      this.version = buf.get();
      this.transactionNumber = ParseTransactionLogs.getUInt(buf);
    }

    public String toString() {
      return "Version: " + version + "\nNumber of Transactions: " + transactionNumber;
    }
  }

  // Class that parses a transaction
  static class Transaction {
    public Type type;
    public long timestamp;
    public double amount;
    public BigInteger userId;

    public Transaction(ByteBuffer buf) {
      byte[] userBytes = new byte[8];
      this.type = Type.values()[buf.get()];
      this.timestamp = ParseTransactionLogs.getUInt(buf);
      buf.get(userBytes);
      this.userId = new BigInteger(1, userBytes);
      if (this.hasAmount()) this.amount = buf.getDouble();
    }

    public String toString() {
      String base = "Type: " + type + " to user " + userId;
      if (this.hasAmount()) base += " for " + amount;
      return base;
    }

    public boolean hasAmount() {
      return type == Type.DEBIT || type == Type.CREDIT;
    }
  }
}
