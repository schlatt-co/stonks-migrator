package tycho.stonks2.model.core;

import tycho.stonks2.model.accountvisitors.IAccountVisitor;
import tycho.stonks2.model.service.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

public class HoldingsAccount extends Account {

  public final Collection<tycho.stonks2.model.core.Holding> holdings;

  public HoldingsAccount(int pk, String name, UUID uuid, int companyPk,
                         Collection<Service> services, Collection<tycho.stonks2.model.core.Holding> holdings) {
    super(pk, name, uuid, companyPk, services);
    this.holdings = holdings;
  }

  public HoldingsAccount(HoldingsAccount holdingsAccount) {
    super(holdingsAccount);
    this.holdings = new ArrayList<>(holdingsAccount.holdings);
  }


  public double getTotalShare() {
    double total = 0;
    for (tycho.stonks2.model.core.Holding h : holdings) {
      total += h.share;
    }
    return total;
  }

  //Returns the holding for the player entered, if none is found then return nothing
  public tycho.stonks2.model.core.Holding getPlayerHolding(UUID player) {
    for (tycho.stonks2.model.core.Holding h : holdings) {
      if (h.playerUUID.equals(player)) {
        return h;
      }
    }
    return null;
  }

  @Override
  public double getTotalBalance() {
    double total = 0;

    for (Holding h : holdings) {
      total += h.balance;
    }
    return total;
  }

  @Override
  public void accept(IAccountVisitor visitor) {
    visitor.visit(this);
  }
}
