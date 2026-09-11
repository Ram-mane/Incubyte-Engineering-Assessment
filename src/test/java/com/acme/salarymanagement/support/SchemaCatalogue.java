package com.acme.salarymanagement.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Reads back what the migrations actually built, from PostgreSQL's own catalogue.
 *
 * <p>Shared by the schema tests so that each one asserts the shape of its table rather than
 * re-deriving how to ask. Every query is parameterised, including these.
 */
public final class SchemaCatalogue {

    private SchemaCatalogue() {}

    /** Column name to type and nullability, exactly as PostgreSQL reports it. */
    public static Map<String, String> columnsOf(JdbcTemplate jdbc, String table) {
        var columns = new LinkedHashMap<String, String>();
        jdbc.queryForList(
                        """
                        SELECT a.attname                             AS name,
                               format_type(a.atttypid, a.atttypmod)  AS type,
                               a.attnotnull                          AS required
                        FROM   pg_attribute a
                        WHERE  a.attrelid = to_regclass(?)
                          AND  a.attnum > 0
                          AND  NOT a.attisdropped
                        ORDER BY a.attnum
                        """,
                        table)
                .forEach(row -> columns.put(
                        (String) row.get("name"),
                        row.get("type") + (Boolean.TRUE.equals(row.get("required")) ? " NOT NULL" : " NULL")));
        return columns;
    }

    public static List<String> primaryKeyOf(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList(
                """
                SELECT a.attname
                FROM   pg_index i
                JOIN   pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = ANY (i.indkey)
                WHERE  i.indrelid = to_regclass(?)
                  AND  i.indisprimary
                """,
                String.class,
                table);
    }

    /** Any column the database would fill in by itself: a DEFAULT expression or an identity. */
    public static List<String> generatedValuesIn(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList(
                """
                SELECT a.attname || ' = ' || COALESCE(pg_get_expr(d.adbin, d.adrelid), 'generated identity')
                FROM   pg_attribute a
                LEFT JOIN pg_attrdef d ON d.adrelid = a.attrelid AND d.adnum = a.attnum
                WHERE  a.attrelid = to_regclass(?)
                  AND  a.attnum > 0
                  AND  NOT a.attisdropped
                  AND  (d.adbin IS NOT NULL OR a.attidentity <> '')
                """,
                String.class,
                table);
    }

    public static List<String> triggersOn(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList(
                "SELECT tgname FROM pg_trigger WHERE tgrelid = to_regclass(?) AND NOT tgisinternal",
                String.class,
                table);
    }

    public static List<String> indexesOn(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE tablename = ?", String.class, table);
    }

    public static List<String> tablesInTheSchema(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname = ?", String.class, "public");
    }

    /** What a role is actually allowed to do to a table, according to the grant catalogue. */
    public static List<String> privilegesOn(JdbcTemplate jdbc, String role, String table) {
        return jdbc.queryForList(
                """
                SELECT privilege_type
                FROM   information_schema.role_table_grants
                WHERE  grantee = ? AND table_name = ?
                """,
                String.class,
                role,
                table);
    }
}
