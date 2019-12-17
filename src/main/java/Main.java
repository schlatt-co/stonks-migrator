import com.j256.ormlite.stmt.QueryBuilder;
import tycho.stonks.managers.DatabaseManager;
import tycho.stonks.model.accountvisitors.ReturningAccountVisitor;
import tycho.stonks.model.core.*;
import tycho.stonks.model.logging.Transaction;
import tycho.stonks2.Repo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

public class Main {
  static Scanner sc = new Scanner(System.in);
  static DatabaseManager databaseManager;


  private static void setupConnections() {
//    System.out.println("Enter hostname");
//    String host = sc.nextLine();
//    System.out.println("Enter port");
//    String port = sc.nextLine();
//    System.out.println("Enter old database name");
//    String old_db = sc.nextLine();
//    System.out.println("Enter new database name");
//    String new_db = sc.nextLine();
//    System.out.println("============");
//    System.out.println("Enter Username");
//    String username = sc.nextLine();
//    System.out.println("Enter Username");
//    String password = sc.nextLine();

    String host = "167.71.7.34";
    String port = "3306";
    String old_db = "stonksmigratefrom";
    String new_db = "stonksmigratetarget";
    String username = "tsarcasm";
    String password = "yeet1";
    databaseManager = new DatabaseManager(host, port, old_db, username, password);
    new Repo(getConnection(host, port, new_db, username, password));

  }

  private static Connection getConnection(String host, String port, String database, String username, String password) {

    boolean useSsl = true;

    String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
        "?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=GMT&useSSL="
        + useSsl;

    Properties connectionProps = new Properties();
    connectionProps.put("user", username);
    connectionProps.put("password", password);
    Connection conn = null;
    try {
      conn = DriverManager.getConnection(url, connectionProps);
      System.out.println("Connected to database");
    } catch (SQLException e) {
      e.printStackTrace();
      System.out.println("Connection to database failed");

    }
    return conn;
  }


  public static void main(String[] args) {
    setupConnections();
    System.out.println("Connections established");
    migrate();
  }

  private static void migrate() {
    List<Company> list;
    try {
      QueryBuilder<Company, UUID> companyQueryBuilder = databaseManager.getCompanyDao().queryBuilder();
      companyQueryBuilder.orderBy("name", true);
      list = companyQueryBuilder.query();
    } catch (SQLException e) {
      e.printStackTrace();
      return;
    }

    HashMap<Company, tycho.stonks2.model.core.Company> companyMap = new HashMap<>();
    HashMap<AccountLink, tycho.stonks2.model.core.Account> accountMap = new HashMap<>();



    for (Company company : list.subList(0, 5)) {
      try {
        Member ceo = company.getMembers().stream().filter(m -> m.getRole() == Role.CEO).findAny().get();
        tycho.stonks2.model.core.Company newCompany = Repo.getInstance().createCompany(company.getName(), ceo.getUuid());
        companyMap.put(company, newCompany);
        System.out.println("Migrated company, pk = " + newCompany.pk + ", name = " + newCompany.name);
        //We have a company with a ceo
        //We now need to add the rest of the members
        for (Member member : company.getMembers()) {
          if (member.getRole() == Role.CEO) continue;
          tycho.stonks2.model.core.Member newMember = Repo.getInstance().createMember(newCompany, member.getUuid());
          System.out.println("\tMigrated member, pk = " + newMember.pk + ", role = " + newMember.role.name());
        }
        System.out.println();
        System.out.println();
        //Now do accounts
        for (AccountLink accountLink : company.getAccounts()) {
          Account account = accountLink.getAccount();
          ReturningAccountVisitor<tycho.stonks2.model.core.Account> visitor
              = new ReturningAccountVisitor<tycho.stonks2.model.core.Account>() {
            @Override
            public void visit(CompanyAccount a) {
              tycho.stonks2.model.core.CompanyAccount ca = new tycho.stonks2.model.core.CompanyAccount(
                  0,
                  a.getName(),
                  a.getUuid(),
                  newCompany.pk,
                  new ArrayList<>(),
                  a.getTotalBalance());
              ca = Repo.getInstance().companyAccounts().create(ca);
              val = ca;
              System.out.println("\tMigrated company account, pk = " + ca.pk + ", balance = " + ca.balance + "$, name = " + ca.name);
            }

            @Override
            public void visit(HoldingsAccount a) {
              tycho.stonks2.model.core.HoldingsAccount ha = new tycho.stonks2.model.core.HoldingsAccount(
                  0,
                  a.getName(),
                  a.getUuid(),
                  newCompany.pk,
                  new ArrayList<>(),
                  new ArrayList<>());
              ha = Repo.getInstance().holdingsAccounts().create(ha);
              val = ha;
              System.out.println("\tMigrated holdings account, pk = " + ha.pk + ", name = " + ha.name);

              //Now load holdings
              for (Holding holding : a.getHoldings()) {
                tycho.stonks2.model.core.Holding h = new tycho.stonks2.model.core.Holding(
                    0,
                    holding.getPlayer(),
                    holding.getBalance(),
                    holding.getShare(),
                    ha.pk
                );
                h = Repo.getInstance().holdings().create(h);
                System.out.println("\t\tMigrated holding, pk = " + h.pk + ", player = " + h.playerUUID + ", balance = " + h.balance + "$");
              }
            }
          };
          account.accept(visitor);
          tycho.stonks2.model.core.Account newAccount = visitor.getRecentVal();
          accountMap.put(accountLink, newAccount);
          //Now do transactions
          List<Transaction> transactions = databaseManager.getTransactionDao()
              .getTransactionsForAccount(accountLink, databaseManager.getAccountLinkDao().queryBuilder(), 1000000, 0);
          for (Transaction transaction : transactions) {
            tycho.stonks2.model.logging.Transaction t = new tycho.stonks2.model.logging.Transaction(
                0,
                newAccount.pk,
                transaction.getPayee(),
                transaction.getMessage(),
                transaction.getAmount(),
                transaction.getTimestamp()
            );
            Repo.getInstance().transactions().create(t);
            System.out.println("\t\tMigrated transaction, message = " + t.message);
          }

          System.out.println();
          System.out.println();


        }

        //Services appear to be broken


        /*

        //Now do services
        for (Service service : company.getServices()) {
          tycho.stonks2.model.service.Service newService = new tycho.stonks2.model.service.Service(
              0,
              service.getName(),
              service.getDuration(),
              service.getCost(),
              service.getMaxSubscriptions(),
              accountMap.get(
                  service.getAccount()
              ).pk,
              new ArrayList<>());
          newService = Repo.getInstance().services().create(newService);
          System.out.println("\tMigrated service, pk = " + newService.pk + " name = " + newService.name);

          for (Subscription subscription : service.getSubscriptions()) {
            tycho.stonks2.model.service.Subscription newSubscription = new tycho.stonks2.model.service.Subscription(
                0,
                subscription.getPlayerId(),
                newService.pk,
                subscription.getLastPaymentDate(),
                true);
            newSubscription = Repo.getInstance().subscriptions().create(newSubscription);
            System.out.println("\t\tMigrated subscription, pk = " + newSubscription.pk + " player = " + newSubscription.playerUUID);
          }
        }
        */
      } catch (Exception e) {
        e.printStackTrace();
        try {
          Thread.sleep(500);
        } catch (InterruptedException ex) {
          ex.printStackTrace();
        }
      }
    }
  }


}
