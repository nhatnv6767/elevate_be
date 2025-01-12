package com.elevatebanking.config.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.List;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.timeout:2000}")
    private Duration timeout;

    @Value("${spring.data.redis.pool.max-active:8}")
    private int maxActive;

    @Value("${spring.data.redis.pool.max-idle:8}")
    private int maxIdle;

    @Value("${spring.data.redis.pool.min-idle:0}")
    private int minIdle;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);

        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .commandTimeout(timeout)
                .clientOptions(ClientOptions.builder()
                        .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                        .socketOptions(SocketOptions.builder()
                                .connectTimeout(timeout)
                                .build())
                        .build())
                .clientResources(DefaultClientResources.builder()
                        .ioThreadPoolSize(4)
                        .computationThreadPoolSize(4)
                        .build())
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfig);
        factory.setValidateConnection(true);
        return factory;
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory,
                                                       MeterRegistry meterRegistry) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(
                new MetricRedisConnectionFactory(connectionFactory, meterRegistry));

        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());

        template.setEnableTransactionSupport(false);
        template.afterPropertiesSet();
        return template;
    }

    @Slf4j
    private static class MetricRedisConnection implements RedisConnection {
        private final RedisConnection delegate;
        private final MeterRegistry meterRegistry;

        public MetricRedisConnection(RedisConnection delegate, MeterRegistry meterRegistry) {
            this.delegate = delegate;
            this.meterRegistry = meterRegistry;
        }

        @Override
        public void close() throws DataAccessException {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                delegate.close();
                sample.stop(Timer.builder("redis.connection.close")
                        .description("Time taken to close Redis connection")
                        .register(meterRegistry));
            } catch (Exception e) {
                sample.stop(Timer.builder("redis.connection.close.failures")
                        .description("Failed Redis connection close attempts")
                        .register(meterRegistry));
                log.error("Failed to close Redis connection", e);
                throw e;
            }
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public Object getNativeConnection() {
            return delegate.getNativeConnection();
        }

        @Override
        public boolean isQueueing() {
            return delegate.isQueueing();
        }

        @Override
        public boolean isPipelined() {
            return delegate.isPipelined();
        }

        @Override
        public void openPipeline() {
            delegate.openPipeline();
        }

        @Override
        public List<Object> closePipeline() throws RedisPipelineException {
            return delegate.closePipeline();
        }

        @Override
        public RedisSentinelConnection getSentinelConnection() {
            return delegate.getSentinelConnection();
        }

        @Override
        public Object execute(String command, byte[]... args) {
            return delegate.execute(command, args);
        }

        @Override
        public RedisCommands commands() {
            return delegate.commands();
        }

        @Override
        public RedisGeoCommands geoCommands() {
            return delegate.geoCommands();
        }

        @Override
        public RedisHashCommands hashCommands() {
            return delegate.hashCommands();
        }

        @Override
        public RedisHyperLogLogCommands hyperLogLogCommands() {
            return delegate.hyperLogLogCommands();
        }

        @Override
        public RedisKeyCommands keyCommands() {
            return delegate.keyCommands();
        }

        @Override
        public RedisListCommands listCommands() {
            return delegate.listCommands();
        }

        @Override
        public RedisSetCommands setCommands() {
            return delegate.setCommands();
        }

        @Override
        public RedisScriptingCommands scriptingCommands() {
            return delegate.scriptingCommands();
        }

        @Override
        public RedisServerCommands serverCommands() {
            return delegate.serverCommands();
        }

        @Override
        public RedisStreamCommands streamCommands() {
            return delegate.streamCommands();
        }

        @Override
        public RedisStringCommands stringCommands() {
            return delegate.stringCommands();
        }

        @Override
        public RedisZSetCommands zSetCommands() {
            return delegate.zSetCommands();
        }

        @Override
        public void select(int dbIndex) {
            delegate.select(dbIndex);
        }

        @Override
        public byte[] echo(byte[] message) {
            return delegate.echo(message);
//            return new byte[0];
        }

        @Override
        public String ping() {
            return delegate.ping();
        }

        @Override
        public boolean isSubscribed() {
            return delegate.isSubscribed();
        }

        @Override
        public Subscription getSubscription() {
            return delegate.getSubscription();
        }

        @Override
        public Long publish(byte[] channel, byte[] message) {
            return delegate.publish(channel, message);
        }

        @Override
        public void subscribe(MessageListener listener, byte[]... channels) {
            delegate.subscribe(listener, channels);
        }

        @Override
        public void pSubscribe(MessageListener listener, byte[]... patterns) {
            delegate.pSubscribe(listener, patterns);
        }

        @Override
        public void multi() {
            delegate.multi();
        }

        @Override
        public List<Object> exec() {
            return delegate.exec();
        }

        @Override
        public void discard() {
            delegate.discard();
        }

        @Override
        public void watch(byte[]... keys) {
            delegate.watch(keys);
        }

        @Override
        public void unwatch() {
            delegate.unwatch();
        }
    }

    @Slf4j
    private static class MetricRedisConnectionFactory implements RedisConnectionFactory {
        private final RedisConnectionFactory delegate;
        private final MeterRegistry meterRegistry;

        public MetricRedisConnectionFactory(RedisConnectionFactory delegate, MeterRegistry meterRegistry) {
            this.delegate = delegate;
            this.meterRegistry = meterRegistry;
        }

        @Override
        public RedisConnection getConnection() {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                RedisConnection connection = delegate.getConnection();
                sample.stop(Timer.builder("redis.connection.acquisition")
                        .description("Time taken to acquire Redis connection")
                        .register(meterRegistry));
                return new MetricRedisConnection(connection, meterRegistry);
            } catch (Exception e) {
                sample.stop(Timer.builder("redis.connection.failures")
                        .description("Failed Redis connection attempts")
                        .register(meterRegistry));
                log.error("Failed to acquire Redis connection", e);
                throw e;
            }
        }

        @Override
        public boolean getConvertPipelineAndTxResults() {
            return false;
        }

        @Override
        public RedisClusterConnection getClusterConnection() {
            return null;
        }

        @Override
        public RedisSentinelConnection getSentinelConnection() {
            return null;
        }

        @Override
        public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
            return null;
        }
    }

}
