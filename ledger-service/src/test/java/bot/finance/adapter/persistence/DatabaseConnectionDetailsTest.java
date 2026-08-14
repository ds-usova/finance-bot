package bot.finance.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import bot.finance.common.boot.PersistenceAdapterTest;
import bot.finance.common.containers.PostgresContainers;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * Integration test for the outbound adapter answering where the pool is connected. It is driven against the real
 * pool the slice builds, since what it answers is read off that pool's own configuration.
 */
@PersistenceAdapterTest
@Import(DatabaseConnectionDetails.class)
class DatabaseConnectionDetailsTest {

    @Autowired
    private DatabaseConnectionDetails connectionDetails;

    @Nested
    @DisplayName("what the pool is connected to")
    class WhatThePoolIsConnectedTo {

        @Test
        @DisplayName("when the pool is asked for its address - then the container's own host and port come back")
        void whenPoolIsAskedForItsAddress_thenContainersOwnHostAndPortComeBack() {
            URI containerUri = containerJdbcUri();

            assertThat(connectionDetails.host()).isEqualTo(containerUri.getHost());
            assertThat(connectionDetails.port()).isEqualTo(containerUri.getPort());
        }

        @Test
        @DisplayName("when the pool is asked for its database - then the name is taken off the URL's path")
        void whenPoolIsAskedForItsDatabase_thenNameIsTakenOffTheUrlPath() {
            assertThat(connectionDetails.databaseName())
                    .isEqualTo(containerJdbcUri().getPath().substring(1))
                    .doesNotStartWith("/");
        }

        @Test
        @DisplayName("when the pool is asked for its credentials - then the container's own user and password come "
                + "back")
        void whenPoolIsAskedForItsCredentials_thenContainersOwnUserAndPasswordComeBack() {
            assertThat(connectionDetails.username()).isEqualTo(PostgresContainers.POSTGRES_CONTAINER.getUsername());
            assertThat(connectionDetails.password()).isEqualTo(PostgresContainers.POSTGRES_CONTAINER.getPassword());
        }

        @Test
        @DisplayName("when the pool is asked for its URL - then the JDBC URL comes back whole")
        void whenPoolIsAskedForItsUrl_thenJdbcUrlComesBackWhole() {
            assertThat(connectionDetails.jdbcUrl()).isEqualTo(PostgresContainers.POSTGRES_CONTAINER.getJdbcUrl());
        }
    }

    private URI containerJdbcUri() {
        return URI.create(PostgresContainers.POSTGRES_CONTAINER.getJdbcUrl().substring("jdbc:".length()));
    }
}
