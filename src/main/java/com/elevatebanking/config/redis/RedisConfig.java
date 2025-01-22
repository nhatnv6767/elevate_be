package com.elevatebanking.config.redis;

import com.elevatebanking.entity.atm.AtmMachine;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.*;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.*;

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

    @Bean
    public RedisCacheConfiguration atmCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .prefixCacheNameWith("atm::")
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }

    @Bean
    public RedisTemplate<String, AtmMachine> atmRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, AtmMachine> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.activateDefaultTyping(mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);
        // Serializers
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);

        template.setEnableDefaultSerializer(false);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisCacheManager atmCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    @Bean
    public RedisCacheConfiguration cacheConfiguration(ObjectMapper mapper) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)));
    }

    @Slf4j
    private record MetricRedisConnection(RedisConnection delegate,
                                         MeterRegistry meterRegistry) implements RedisConnection {

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
        public @NotNull Object getNativeConnection() {
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
        public @NotNull List<Object> closePipeline() throws RedisPipelineException {
            return delegate.closePipeline();
        }

        @Override
        public @NotNull RedisSentinelConnection getSentinelConnection() {
            return delegate.getSentinelConnection();
        }

        @Override
        public Object execute(@NotNull String command, byte[] @NotNull ... args) {
            return delegate.execute(command, args);
        }

        @Override
        public @NotNull RedisCommands commands() {
            return delegate.commands();
        }

        @Override
        public @NotNull RedisGeoCommands geoCommands() {
            return delegate.geoCommands();
        }

        @Override
        public @NotNull RedisHashCommands hashCommands() {
            return delegate.hashCommands();
        }

        @Override
        public @NotNull RedisHyperLogLogCommands hyperLogLogCommands() {
            return delegate.hyperLogLogCommands();
        }

        @Override
        public @NotNull RedisKeyCommands keyCommands() {
            return delegate.keyCommands();
        }

        @Override
        public @NotNull RedisListCommands listCommands() {
            return delegate.listCommands();
        }

        @Override
        public @NotNull RedisSetCommands setCommands() {
            return delegate.setCommands();
        }

        @Override
        public @NotNull RedisScriptingCommands scriptingCommands() {
            return delegate.scriptingCommands();
        }

        @Override
        public @NotNull RedisServerCommands serverCommands() {
            return delegate.serverCommands();
        }

        @Override
        public @NotNull RedisStreamCommands streamCommands() {
            return delegate.streamCommands();
        }

        @Override
        public @NotNull RedisStringCommands stringCommands() {
            return delegate.stringCommands();
        }

        @Override
        public @NotNull RedisZSetCommands zSetCommands() {
            return delegate.zSetCommands();
        }

        @Override
        public void select(int dbIndex) {
            delegate.select(dbIndex);
        }

        @Override
        public byte[] echo(byte @NotNull [] message) {
            return delegate.echo(message);
            // return new byte[0];
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
        public Long publish(byte @NotNull [] channel, byte @NotNull [] message) {
            return delegate.publish(channel, message);
        }

        @Override
        public void subscribe(@NotNull MessageListener listener, byte[] @NotNull ... channels) {
            delegate.subscribe(listener, channels);
        }

        @Override
        public void pSubscribe(@NotNull MessageListener listener, byte[] @NotNull ... patterns) {
            delegate.pSubscribe(listener, patterns);
        }

        @Override
        public void multi() {
            delegate.multi();
        }

        @Override
        public @NotNull List<Object> exec() {
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
    private record MetricRedisConnectionFactory(RedisConnectionFactory delegate,
                                                MeterRegistry meterRegistry) implements RedisConnectionFactory {

        @Override
        public @NotNull RedisConnection getConnection() {
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
            return delegate.getConvertPipelineAndTxResults();
        }

        @Override
        public @NotNull RedisClusterConnection getClusterConnection() {
            delegate.getClusterConnection();
            return new MetricRedisClusterConnection(delegate.getClusterConnection(), meterRegistry);
        }

        @Override
        public @NotNull RedisSentinelConnection getSentinelConnection() {
            delegate.getSentinelConnection();
            return delegate.getSentinelConnection();
        }

        @Override
        public DataAccessException translateExceptionIfPossible(RuntimeException ex) {
            return delegate.translateExceptionIfPossible(ex);
        }
    }

    @Slf4j
    private record MetricRedisClusterConnection(RedisClusterConnection delegate,
                                                MeterRegistry meterRegistry) implements RedisClusterConnection {

        @Override
        public @NotNull String ping(@NotNull RedisClusterNode node) {
            return Objects.requireNonNull(delegate.ping(node));
        }

        @Override
        public @NotNull Set<byte[]> keys(@NotNull RedisClusterNode node, byte[] pattern) {
            return Objects.requireNonNull(delegate.keys(node, pattern));
        }

        @Override
        public @NotNull Cursor<byte[]> scan(@NotNull RedisClusterNode node, @NotNull ScanOptions options) {
            return delegate.scan(node, options);
        }

        @Override
        public byte[] randomKey(@NotNull RedisClusterNode node) {
            return delegate.randomKey(node);
        }

        @Override
        public @NotNull RedisClusterCommands clusterCommands() {
            return delegate.clusterCommands();
        }

        @Override
        public @NotNull RedisCommands commands() {
            return delegate.commands();
        }

        @Override
        public @NotNull RedisGeoCommands geoCommands() {
            return delegate.geoCommands();
        }

        @Override
        public @NotNull RedisHashCommands hashCommands() {
            return delegate.hashCommands();
        }

        @Override
        public @NotNull RedisHyperLogLogCommands hyperLogLogCommands() {
            return delegate.hyperLogLogCommands();
        }

        @Override
        public @NotNull RedisKeyCommands keyCommands() {
            return delegate.keyCommands();
        }

        @Override
        public @NotNull RedisListCommands listCommands() {
            return delegate.listCommands();
        }

        @Override
        public @NotNull RedisSetCommands setCommands() {
            return delegate.setCommands();
        }

        @Override
        public @NotNull RedisScriptingCommands scriptingCommands() {
            return delegate.scriptingCommands();
        }

        @Override
        public @NotNull RedisClusterServerCommands serverCommands() {
            return delegate.serverCommands();
        }

        @Override
        public @NotNull RedisStreamCommands streamCommands() {
            return delegate.streamCommands();
        }

        @Override
        public @NotNull RedisStringCommands stringCommands() {
            return delegate.stringCommands();
        }

        @Override
        public @NotNull RedisZSetCommands zSetCommands() {
            return delegate.zSetCommands();
        }

        @Override
        public void close() throws DataAccessException {
            delegate.close();
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public @NotNull Object getNativeConnection() {
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
        public @NotNull List<Object> closePipeline() throws RedisPipelineException {
            return delegate.closePipeline();
        }

        @Override
        public @NotNull RedisSentinelConnection getSentinelConnection() {
            return delegate.getSentinelConnection();
        }

        @Override
        public @NotNull Iterable<RedisClusterNode> clusterGetNodes() {
            return delegate.clusterGetNodes();
        }

        @Override
        public @NotNull Collection<RedisClusterNode> clusterGetReplicas(@NotNull RedisClusterNode master) {
            return delegate.clusterGetReplicas(master);
        }

        @Override
        public @NotNull Map<RedisClusterNode, Collection<RedisClusterNode>> clusterGetMasterReplicaMap() {
            return delegate.clusterGetMasterReplicaMap();
        }

        @Override
        public @NotNull Integer clusterGetSlotForKey(byte[] key) {
            return delegate.clusterGetSlotForKey(key);
        }

        @Override
        public @NotNull RedisClusterNode clusterGetNodeForSlot(int slot) {
            return delegate.clusterGetNodeForSlot(slot);
        }

        @Override
        public @NotNull RedisClusterNode clusterGetNodeForKey(byte[] key) {
            return delegate.clusterGetNodeForKey(key);
        }

        @Override
        public @NotNull ClusterInfo clusterGetClusterInfo() {
            return delegate.clusterGetClusterInfo();
        }

        @Override
        public void clusterAddSlots(@NotNull RedisClusterNode node, int... slots) {
            delegate.clusterAddSlots(node, slots);
        }

        @Override
        public void clusterAddSlots(@NotNull RedisClusterNode node, @NotNull RedisClusterNode.SlotRange range) {
            delegate.clusterAddSlots(node, range);
        }

        @Override
        public @NotNull Long clusterCountKeysInSlot(int slot) {
            return delegate.clusterCountKeysInSlot(slot);
        }

        @Override
        public void clusterDeleteSlots(@NotNull RedisClusterNode node, int... slots) {
            delegate.clusterDeleteSlots(node, slots);
        }

        @Override
        public void clusterDeleteSlotsInRange(@NotNull RedisClusterNode node,
                                              @NotNull RedisClusterNode.SlotRange range) {
            delegate.clusterDeleteSlotsInRange(node, range);
        }

        @Override
        public void clusterForget(@NotNull RedisClusterNode node) {
            delegate.clusterForget(node);
        }

        @Override
        public void clusterMeet(@NotNull RedisClusterNode node) {
            delegate.clusterMeet(node);
        }

        @Override
        public void clusterSetSlot(@NotNull RedisClusterNode node, int slot, @NotNull AddSlots mode) {
            delegate.clusterSetSlot(node, slot, mode);
        }

        @Override
        public @NotNull List<byte[]> clusterGetKeysInSlot(int slot, Integer count) {
            return delegate.clusterGetKeysInSlot(slot, count);
        }

        @Override
        public void clusterReplicate(@NotNull RedisClusterNode master, @NotNull RedisClusterNode replica) {
            delegate.clusterReplicate(master, replica);
        }

        @Override
        public @NotNull Object execute(@NotNull String command, byte[]... args) {
            return Objects.requireNonNull(delegate.execute(command, args));
        }

        @Override
        public void select(int dbIndex) {
            delegate.select(dbIndex);
        }

        @Override
        public byte[] echo(byte[] message) {
            return delegate.echo(message);
        }

        @Override
        public @NotNull String ping() {
            return Objects.requireNonNull(delegate.ping());
        }

        @Override
        public boolean isSubscribed() {
            return delegate.isSubscribed();
        }

        @Override
        public @NotNull Subscription getSubscription() {
            return Objects.requireNonNull(delegate.getSubscription());
        }

        @Override
        public @NotNull Long publish(byte[] channel, byte[] message) {
            return Objects.requireNonNull(delegate.publish(channel, message));
        }

        @Override
        public void subscribe(@NotNull MessageListener listener, byte[]... channels) {
            delegate.subscribe(listener, channels);
        }

        @Override
        public void pSubscribe(@NotNull MessageListener listener, byte[]... patterns) {
            delegate.pSubscribe(listener, patterns);
        }

        @Override
        public void multi() {
            delegate.multi();
        }

        @Override
        public @NotNull List<Object> exec() {
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

}
