package tycho.stonks.database;

import com.j256.ormlite.dao.Dao;
import com.j256.ormlite.stmt.QueryBuilder;
import org.bukkit.entity.Player;
import tycho.stonks.model.core.Company;
import tycho.stonks.model.core.Member;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface CompanyDao extends Dao<Company, UUID> {
  boolean companyExists(String name) throws SQLException;

  Company getCompany(String name) throws SQLException;

  List<Company> getAllCompanies();

  List<Company> getAllCompaniesWhereManager(Player player, QueryBuilder<Member, UUID> memberQuery);
}
