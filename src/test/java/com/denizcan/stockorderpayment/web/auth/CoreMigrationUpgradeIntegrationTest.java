package com.denizcan.stockorderpayment.web.auth;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;

class CoreMigrationUpgradeIntegrationTest {
    @Test void populatedPreviousSchemaKeepsAccountsTagsAndLocationBalances()throws Exception {
        String url=System.getProperty("core.upgrade.datasource.url",
                "jdbc:h2:mem:core_upgrade;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        Flyway.configure().dataSource(url,"coretest","").locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("19")).load().migrate();
        try(Connection c=DriverManager.getConnection(url,"coretest","");Statement s=c.createStatement()) {
            s.executeUpdate("insert into branches(id,name) values(101,'Existing branch')");
            s.executeUpdate("insert into employee_roles(id,name,name_key,scope) values(101,'Existing role','existing role','USER')");
            s.executeUpdate("insert into employee_role_permissions(role_id,permission) values(101,'PRODUCTS_VIEW')");
            s.executeUpdate("insert into users(id,username,password,role,enabled,employee_role_id) values(101,'existing-worker','unchanged-hash','USER',true,101)");
            s.executeUpdate("insert into users(id,username,password,role,enabled,branch_id) values(102,'existing-branch','unchanged-branch-hash','BRANCH_EMPLOYEE',true,101)");
            s.executeUpdate("insert into products(id,name,sku,price,quantity) values(101,'Existing product','EXISTING-001',null,7)");
            s.executeUpdate("insert into stock(product_id,quantity,reserved_quantity) values(101,5,1)");
            s.executeUpdate("insert into branch_stock(branch_id,product_id,quantity) values(101,101,2)");
            s.executeUpdate("insert into product_tags(product_id,name,tag_value) values(101,'Marka','Arçelik')");
        }
        var result=Flyway.configure().dataSource(url,"coretest","").locations("classpath:db/migration").load().migrate();
        assertThat(result.migrationsExecuted).isEqualTo(3);
        try(Connection c=DriverManager.getConnection(url,"coretest","");Statement s=c.createStatement()) {
            try(ResultSet r=s.executeQuery("select quantity from products where id=101")){assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isEqualTo(7);}
            try(ResultSet r=s.executeQuery("select quantity,reserved_quantity from stock where product_id=101")){assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isEqualTo(5);assertThat(r.getInt(2)).isEqualTo(1);}
            try(ResultSet r=s.executeQuery("select quantity,version from branch_stock where product_id=101")){assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isEqualTo(2);assertThat(r.getLong(2)).isZero();}
            try(ResultSet r=s.executeQuery("select tag_value from product_tags where product_id=101")){assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("Arçelik");}
            try(ResultSet r=s.executeQuery("select password,employee_role_id from users where id=101")){assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("unchanged-hash");assertThat(r.getLong(2)).isEqualTo(101);}
            try(ResultSet r=s.executeQuery("select version from employee_roles where id=101")){assertThat(r.next()).isTrue();assertThat(r.getLong(1)).isZero();}
            // New optional branch assignment is accepted for the existing ordinary account.
            assertThat(s.executeUpdate("update users set branch_id=101 where id=101")).isEqualTo(1);
            try(ResultSet r=s.executeQuery("select count(*) from users")){r.next();assertThat(r.getInt(1)).isEqualTo(2);}
        }
    }
}
