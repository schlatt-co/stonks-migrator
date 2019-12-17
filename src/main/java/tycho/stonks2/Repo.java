package tycho.stonks2;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import tycho.stonks2.database.AsyncSaveStore;
import tycho.stonks2.database.DatabaseStore;
import tycho.stonks2.database.Store;
import tycho.stonks2.database.TransactionStore;
import tycho.stonks2.model.accountvisitors.IAccountVisitor;
import tycho.stonks2.model.accountvisitors.ReturningAccountVisitor;
import tycho.stonks2.model.core.*;
import tycho.stonks2.model.dbis.*;
import tycho.stonks2.model.logging.Transaction;
import tycho.stonks2.model.service.Service;
import tycho.stonks2.model.service.Subscription;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

//The repo has a store for each entity we want to save in the database
public class Repo {

  private static Repo instance;
  private Connection conn;
  private DatabaseStore<Company> companyStore;
  private DatabaseStore<CompanyAccount> companyAccountStore;
  private DatabaseStore<HoldingsAccount> holdingsAccountStore;
  private DatabaseStore<Holding> holdingStore;
  private DatabaseStore<Member> memberStore;
  private DatabaseStore<Service> serviceStore;
  private DatabaseStore<Subscription> subscriptionStore;
  private TransactionStore transactionStore;

  public Repo(Connection connection) {
    instance = this;
    conn = connection;
    if (connection == null) return;
    companyStore = new AsyncSaveStore<>(Company::new);
    companyAccountStore = new AsyncSaveStore<>(CompanyAccount::new);
    holdingsAccountStore = new AsyncSaveStore<>(HoldingsAccount::new);
    holdingStore = new AsyncSaveStore<>(Holding::new);
    memberStore = new AsyncSaveStore<>(Member::new);
    serviceStore = new AsyncSaveStore<>(Service::new);
    subscriptionStore = new AsyncSaveStore<>(Subscription::new);
    transactionStore = new TransactionStore(conn, new TransactionDBI(conn));
    transactionStore.createTable();


    companyStore.setDbi(new CompanyDBI(conn, memberStore, companyAccountStore, holdingsAccountStore));
    companyAccountStore.setDbi(new CompanyAccountDBI(conn, serviceStore));
    holdingsAccountStore.setDbi(new HoldingsAccountDBI(conn, serviceStore, holdingStore));
    holdingStore.setDbi(new HoldingDBI(conn));
    memberStore.setDbi(new MemberDBI(conn));
    serviceStore.setDbi(new ServiceDBI(conn, subscriptionStore));
    subscriptionStore.setDbi(new SubscriptionDBI(conn));

    repopulateAll();
  }

  public static Repo getInstance() {
    return instance;
  }

//  private Connection createConnection() throws SQLException {
//    String host = plugin.getConfig().getString("mysql.host");
//    String port = plugin.getConfig().getString("mysql.port");
//    String database = plugin.getConfig().getString("mysql.new_database");
//    String username = plugin.getConfig().getString("mysql.username");
//    String password = plugin.getConfig().getString("mysql.password");
//    String useSsl = plugin.getConfig().getString("mysql.ssl");
//    String url = "jdbc:mysql://" + host + ":" + port + "/" + database +
//        "?useUnicode=true&useJDBCCompliantTimezoneShift=true&useLegacyDatetimeCode=false&serverTimezone=GMT&useSSL="
//        + useSsl;
//
//    Properties connectionProps = new Properties();
//    connectionProps.put("user", username);
//    connectionProps.put("password", password);
//    Connection conn = DriverManager.getConnection(url, connectionProps);
//    System.out.println("Connected to database");
//    return conn;
//  }

  //todo this could be in a better place than repo
  public int getNextAccountPk() {
    try {
      int companyAccountPk = -1;
      int holdingsAccountPk = -1;
      PreparedStatement statement = conn.prepareStatement(
          "SHOW TABLE STATUS LIKE 'company_account'");
      ResultSet results = statement.executeQuery();
      while (results.next()) {
        companyAccountPk = results.getInt("Auto_increment");
      }
      statement = conn.prepareStatement(
          "SHOW TABLE STATUS LIKE 'holdings_account'");
      results = statement.executeQuery();
      while (results.next()) {
        holdingsAccountPk = results.getInt("Auto_increment");
      }
      if (holdingsAccountPk == -1 || companyAccountPk == -1) {
        System.out.println("Couldn't get a primary key for an account");
        return -1;
      }

      return Math.max(companyAccountPk, holdingsAccountPk);
    } catch (SQLException e) {
      e.printStackTrace();
      return -1;
    }
  }


  public void repopulateAll() {
    subscriptionStore.populate();
    serviceStore.populate();
    companyAccountStore.populate();
    holdingStore.populate();
    holdingsAccountStore.populate();
    memberStore.populate();
    companyStore.populate();
  }


  public Collection<Company> companiesWhereManager(Player player) {
    Collection<Company> list = new ArrayList<>(Repo.getInstance().companies().getAll());
    list.removeIf(c -> {
      for (Member m : c.members) {
        //If a manager then keep
        if (m.playerUUID.equals(player.getUniqueId()) && m.hasManagamentPermission()) return false;
      }
      return true;
    });
    return list;
  }

  public Collection<Company> companiesWithWithdrawableAccount(Player player) {
    List<Company> list = new ArrayList<>(Repo.getInstance().companies().getAll());
    //We need a list of all companies with a withdrawable account for this player
    //Remove companies where the player is not a manager and doesn't have an account
    //todo remove this messy logic
    for (int i = list.size() - 1; i >= 0; i--) {
      boolean remove = true;
      Company c = list.get(i);
      Member m = c.getMember(player);
      if (m != null && m.hasManagamentPermission()) {
        //If a manager or ceo
        remove = false;
      }
      //If you are not a manager, or a non-member with a holding then don't remove
      for (Account a : c.accounts) {
        //Is there a holding account for the player
        ReturningAccountVisitor<Boolean> visitor = new ReturningAccountVisitor<Boolean>() {
          @Override
          public void visit(CompanyAccount a) {
            val = false;
          }

          @Override
          public void visit(HoldingsAccount a) {
            val = (a.getPlayerHolding(player.getUniqueId()) != null);
          }
        };
        a.accept(visitor);
        if (visitor.getRecentVal()) remove = false;
      }
      if (remove) list.remove(i);
    }
    return list;
  }


  public Company companyWithName(String name) {
    return Repo.getInstance().companies().getWhere(c -> c.name.equals(name));
  }

  //Find the account with a given id. Will return null if none is found
  public Account accountWithId(int id) {
    Account a = holdingsAccountStore.get(id);
    if (a == null) {
      a = companyAccountStore.get(id);
    }
    return a;
  }

  public Account accountWithUUID(UUID uuid) {
    Account a = holdingsAccountStore.getWhere(c -> c.uuid.equals(uuid));
    if (a == null) {
      a = companyAccountStore.getWhere(c -> c.uuid.equals(uuid));
    }
    return a;
  }


  public Company createCompany(String companyName, UUID playerUUID) {
    Company c = new Company(0, companyName, "S" + companyName,
        Material.EMERALD.name(), false, false, new ArrayList<>(), new ArrayList<>()
    );
    c = companyStore.create(c);

    Member ceo = new Member(0, playerUUID, c.pk, new Timestamp(System.currentTimeMillis()), Role.CEO, true);
    memberStore.create(ceo);
    companyStore.refreshRelations(c.pk);
    return c;
  }

  public Company modifyCompany(Company c, String newName, String newLogo, boolean newVerified, boolean newHidden) {
    Company company = new Company(c.pk, newName, "S" + newName,
        newLogo, newVerified, newHidden, c.accounts, c.members
    );
    companyStore.save(company);
    return company;
  }

  public Member createMember(Company company, UUID playerUUID) {
    Member newMember = new Member(0, playerUUID, company.pk, new Timestamp(System.currentTimeMillis()), Role.Employee, false);
    newMember = memberStore.create(newMember);
    companyStore.refreshRelations(newMember.pk);
    return newMember;
  }

  public Member modifyMember(Member m, Role newRole, boolean newAcceptedInvite) {
    Member member = new Member(m.pk, m.playerUUID, m.companyPk, m.joinTimestamp, newRole, newAcceptedInvite);
    memberStore.save(member);
    companyStore.refreshRelations(m.companyPk);
    return member;
  }

  public boolean deleteMember(Member m) {
    if (memberStore.delete(m.pk)) {
      //Update the company for this to remove the member
      companyStore.refreshRelations(m.companyPk);
      return true;
    }
    return false;
  }

  public Collection<Member> getInvites(Player player) {
    return memberStore.getAllWhere(member -> !member.acceptedInvite && member.playerUUID.equals(player.getUniqueId()));
  }

  public void createTransaction(UUID player, Account account, String message, double amount) {
    //Create the new transaction
    Transaction t = new Transaction(0, account.pk, player, message, amount,
        new Timestamp(System.currentTimeMillis()));
    transactionStore.create(t);
    //No refreshes are needed since no entities have a collection of transactions
  }

  public HoldingsAccount createHoldingsAccount(Company company, String name, Player player) {
    HoldingsAccount ha = new HoldingsAccount(0, name, UUID.randomUUID(), company.pk, new ArrayList<>(), new ArrayList<>());
    ha = holdingsAccountStore.create(ha);

    Holding h = new Holding(0, player.getUniqueId(), 0, 1, ha.pk);
    holdingStore.create(h);
    holdingsAccountStore.refreshRelations(ha.pk);
    companyStore.refreshRelations(company.pk);
    return ha;
  }

  public CompanyAccount createCompanyAccount(Company company, String name) {
    CompanyAccount ca = new CompanyAccount(0, name, UUID.randomUUID(), company.pk, new ArrayList<>(), 0);
    ca = companyAccountStore.create(ca);
    companyStore.refreshRelations(company.pk);
    return ca;
  }

  private void refreshAccount(Account account) {
    account.accept(new IAccountVisitor() {
      @Override
      public void visit(CompanyAccount a) {
        companyAccountStore.refreshRelations(a.pk);
      }

      @Override
      public void visit(HoldingsAccount a) {
        holdingsAccountStore.refreshRelations(a.pk);
      }
    });
  }

  public Account renameAccount(Account account, String newName) {
    ReturningAccountVisitor<Account> visitor = new ReturningAccountVisitor<Account>() {
      @Override
      public void visit(CompanyAccount a) {
        CompanyAccount ca = new CompanyAccount(a.pk, newName, a.uuid, a.companyPk, a.services, a.balance);
        companyAccountStore.save(ca);
        val = ca;
      }

      @Override
      public void visit(HoldingsAccount a) {
        HoldingsAccount ha = new HoldingsAccount(a.pk, newName, a.uuid, a.companyPk, a.services, a.holdings);
        holdingsAccountStore.save(ha);
        val = ha;
      }
    };
    account.accept(visitor);
    Account a = visitor.getRecentVal();
    companyStore.refreshRelations(a.companyPk);
    return a;
  }

  public Account payAccount(UUID player, String message, Account account, double amount) {
    ReturningAccountVisitor<Account> visitor = new ReturningAccountVisitor<Account>() {
      @Override
      public void visit(CompanyAccount a) {
        CompanyAccount ca = new CompanyAccount(a.pk, a.name, a.uuid, a.companyPk, a.services, a.balance + amount);
        companyAccountStore.save(ca);
        val = ca;
      }

      @Override
      public void visit(HoldingsAccount a) {
        HoldingsAccount ha = new HoldingsAccount(a.pk, a.name, a.uuid, a.companyPk, a.services, a.holdings);
        double totalShare = ha.getTotalShare();
        //Add money proportionally to all holdings
        for (Holding h : ha.holdings) {
          Holding newHolding = new Holding(h.pk, h.playerUUID, h.balance + (h.share / totalShare) * amount, h.share, h.accountPk);
          holdingStore.save(newHolding);
        }
        holdingsAccountStore.save(ha);
        //Refresh to account for holding changes
        holdingsAccountStore.refreshRelations(ha.pk);
        val = holdingsAccountStore.get(ha.pk);
      }
    };
    account.accept(visitor);
    Account a = visitor.getRecentVal();
    //Create a transaction log too
    createTransaction(player, account, message, amount);
    //We don't need to refresh the company because this is done when creating a transaction log
    //companyStore.refresh(a.companyPk);
    return a;
  }

  public Account withdrawFromAccount(UUID player, CompanyAccount a, double amount) {
    if (amount < 0) {
      System.out.println("Should we be withdrawing a -ve amount?");
      throw new IllegalArgumentException("Tried to withdraw a negative amount");
    }
    CompanyAccount ca = new CompanyAccount(a.pk, a.name, a.uuid, a.companyPk, a.services, a.balance - amount);
    companyAccountStore.save(ca);
    //Create a transaction log too
    createTransaction(player, a, "withdraw", -amount);
    //We don't need to refresh the company because this is done when creating a transaction log
    //companyStore.refresh(a.companyPk);
    return ca;
  }

  public Holding createHolding(UUID player, HoldingsAccount holdingsAccount, double share) {
    Holding holding = new Holding(0, player, 0, share, holdingsAccount.pk);
    holding = holdingStore.create(holding);
    if (holding == null) {
      // error creating holding, we should never get here
      return null;
    }
    //Update account and company to persist the new holding
    holdingsAccountStore.refreshRelations(holding.accountPk);
    companyStore.refreshRelations(holdingsAccount.companyPk);
    return holding;
  }

  public Holding withdrawFromHolding(UUID player, Holding h, double amount) {
    Holding holding = new Holding(h.pk, h.playerUUID, h.balance - amount, h.share, h.accountPk);
    holdingStore.save(holding);
    holdingsAccountStore.refreshRelations(h.accountPk);

    createTransaction(player, accountWithId(h.accountPk), "withdraw holding", -amount);
    return holding;
  }

  public boolean deleteHolding(Holding holding) {
    if (memberStore.delete(holding.pk)) {
      //Update the company and account
      holdingsAccountStore.refreshRelations(holding.accountPk);
      HoldingsAccount ha = holdingsAccountStore.get(holding.accountPk);
      companyStore.refreshRelations(ha.companyPk);
      return true;
    }
    return false;
  }

  public Service createService(String name, double duration, double cost, int maxSubscribers, Account account) {
    Service service = new Service(0, name, duration, cost, maxSubscribers, account.pk, new ArrayList<>());
    service = serviceStore.create(service);
    refreshAccount(account);
    companyStore.refreshRelations(account.companyPk);
    return service;
  }

  public Service modifyService(Service s, String name, double duration, double cost, int maxSubscribers) {
    Service service = new Service(s.pk, s.name, duration, cost, maxSubscribers, s.accountPk, s.subscriptions);
    serviceStore.save(service);
    Account a = accountWithId(service.accountPk);
    refreshAccount(a);
    companyStore.refreshRelations(a.companyPk);
    return service;
  }

  public Subscription createSubscription(Player player, Service service, boolean autoPay) {
    Subscription subscription = new Subscription(0, player.getUniqueId(), service.pk, new Timestamp(System.currentTimeMillis()), autoPay);
    subscription = subscriptionStore.create(subscription);
    serviceStore.refreshRelations(service.pk);
    Account account = accountWithId(service.accountPk);
    refreshAccount(account);
    companyStore.refreshRelations(account.companyPk);
    return subscription;
  }

  public Subscription paySubscription(UUID player, Subscription subscription, Service service) {
    Subscription s = new Subscription(subscription.pk, subscription.playerUUID, subscription.servicePk,
        new Timestamp(System.currentTimeMillis()), subscription.autoPay);

    subscriptionStore.save(s);
    if (service.pk != s.servicePk) throw new IllegalArgumentException("Primary Key mismatch");
    serviceStore.refreshRelations(subscription.servicePk);
    //This action also refreshes the account and company
    payAccount(player, "Subscription payment (" + service.name + ")", accountWithId(service.accountPk), service.cost);
    return s;
  }

  public boolean deleteSubscription(Subscription subscription, Service service) {
    if (subscriptionStore.delete(subscription.pk)) {
      serviceStore.refreshRelations(service.pk);
      Account account = accountWithId(service.accountPk);
      refreshAccount(account);
      companyStore.refreshRelations(account.companyPk);
      return true;
    }
    return false;
  }


  public Store<Company> companies() {
    return companyStore;
  }

  public Store<HoldingsAccount> holdingsAccounts() {
    return holdingsAccountStore;
  }

  public Store<CompanyAccount> companyAccounts() {
    return companyAccountStore;
  }

  public Store<Holding> holdings() {
    return holdingStore;
  }

  public Store<Member> members() {
    return memberStore;
  }

  public Store<Service> services() {
    return serviceStore;
  }

  public Store<Subscription> subscriptions() {
    return subscriptionStore;
  }

  public TransactionStore transactions() {
    return transactionStore;
  }

}
