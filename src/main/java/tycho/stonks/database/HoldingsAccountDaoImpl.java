package tycho.stonks.database;

import com.j256.ormlite.dao.BaseDaoImpl;
import com.j256.ormlite.support.ConnectionSource;
import tycho.stonks.model.core.HoldingsAccount;

import java.sql.SQLException;

public class HoldingsAccountDaoImpl extends BaseDaoImpl<HoldingsAccount, Integer> {
  public HoldingsAccountDaoImpl(ConnectionSource connectionSource) throws SQLException {
    super(connectionSource, HoldingsAccount.class);
  }

}
