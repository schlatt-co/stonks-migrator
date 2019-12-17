package tycho.stonks2.model.accountvisitors;

import tycho.stonks2.model.core.CompanyAccount;
import tycho.stonks2.model.core.HoldingsAccount;

public interface IAccountVisitor {
  void visit(CompanyAccount a);

  void visit(HoldingsAccount a);
}
