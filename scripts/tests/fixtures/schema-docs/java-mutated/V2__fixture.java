final class V2__fixture {
    void migrate(java.sql.Statement statement) throws java.sql.SQLException {
        statement.execute("""
                alter table children
                add constraint children_java_parent_fk
                foreign key (java_parent_id) references parents(code)
                """);
    }
}
