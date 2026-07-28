/**
 * Everything fronting the AI Connector Service: the outbound gRPC client, its channel and stub
 * configuration, its domain-to-proto mapper, and the actuator health check derived from the connector's
 * own gRPC health service. Every gRPC and generated proto type stays inside this package.
 */
package bot.finance.adapter.aiconnector;
