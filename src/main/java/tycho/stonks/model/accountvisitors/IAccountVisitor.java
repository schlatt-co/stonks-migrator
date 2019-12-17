package tycho.stonks.model.accountvisitors;

import tycho.stonks.model.core.CompanyAccount;
import tycho.stonks.model.core.HoldingsAccount;

public interface IAccountVisitor {
  void visit(CompanyAccount a);

  void visit(HoldingsAccount a);
}
