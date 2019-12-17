package tycho.stonks.model.logging;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;
import tycho.stonks.model.core.AccountLink;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.UUID;

@DatabaseTable(tableName = "transaction")
public class Transaction {
  @DatabaseField(generatedId = true)
  private int id;

  @DatabaseField(foreign = true, foreignAutoCreate = true, foreignAutoRefresh = true)
  private AccountLink account;

  @DatabaseField()
  private UUID payee = null;

  //negative amount represents money withdrawn
  @DatabaseField()
  private double amount;

  @DatabaseField()
  private Timestamp timestamp;

  @DatabaseField()
  private String message;

  public Transaction() {
  }

  public Transaction(AccountLink account, UUID payee, String message, double amount) {
    this.account = account;
    this.payee = payee;
    this.message = message;
    if (this.message == null) this.message = "";
    this.amount = amount;
    this.timestamp = new Timestamp(Calendar.getInstance().getTime().getTime());
  }

  public UUID getPayee() {
    return payee;
  }

  public Timestamp getTimestamp() {
    return timestamp;
  }

  public String getMessage() {
    return message;
  }

  public double getAmount() {
    return amount;
  }

  public int getId() {
    return id;
  }
}
