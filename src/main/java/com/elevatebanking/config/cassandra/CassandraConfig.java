package com.elevatebanking.config.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;
import org.springframework.data.cassandra.config.SchemaAction;
import org.springframework.data.cassandra.core.CassandraTemplate;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;
import org.springframework.data.cassandra.core.cql.keyspace.CreateKeyspaceSpecification;
import org.springframework.data.cassandra.core.cql.keyspace.KeyspaceOption;

import java.util.*;

@Configuration
@EnableCassandraRepositories(basePackages = "com.elevatebanking.repository.cassandra")
public class CassandraConfig extends AbstractCassandraConfiguration {

    @Value("${spring.cassandra.keyspace-name}")
    private String keyspaceName;

    @Value("${spring.cassandra.contact-points}")
    private String contactPoints;

    @Value("${spring.cassandra.port}")
    private int port;

    @Override
    protected @NotNull String getKeyspaceName() {
        return keyspaceName;
    }

    @Override
    public @NotNull String getContactPoints() {
        return contactPoints;
    }

    @Override
    public int getPort() {
        return port;
    }

    @Override
    public @NotNull SchemaAction getSchemaAction() {
        return SchemaAction.CREATE_IF_NOT_EXISTS;
    }

    @Override
    public @NotNull String[] getEntityBasePackages() {
        return new String[]{
                "com.elevatebanking.entity",
                "com.elevatebanking.entity.atm"
        };
    }

    @Override
    public @NotNull List<CreateKeyspaceSpecification> getKeyspaceCreations() {
        CreateKeyspaceSpecification specification = CreateKeyspaceSpecification
                .createKeyspace(getKeyspaceName())
                .ifNotExists()
                .withSimpleReplication(1);
        return Collections.singletonList(specification);
    }

    @Bean
    public CassandraTemplate cassandraTemplate(CqlSession session) {
        return new CassandraTemplate(session);
    }
}
