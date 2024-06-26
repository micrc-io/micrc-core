package io.micrc.core.persistence;

import io.micrc.core.persistence.snowflake.SnowFlakeIdentity;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 资源库基础接口，所有资源库接口应该继承这个接口作为jpa仓库
 * 自带缓存支持，具体资源库必须注解@CacheConfig并配置缓存名称(按聚合名称配置), 自定义方法必须配置
 * 为@Cacheable(cacheResolver = "repositoryCacheResolver", keyGenerator = "repositoryQueryKeyGenerator", sync = true)
 * 所有实体缓存在名为配置缓存名称后缀-entity的缓存中，由save和saveAndFlush存入，以实体id为key，count也在其中，findById从中取值
 * 除findById外的所有查询缓存在配置缓存名称的缓存中，由方法名+所有参数值为key
 * save/saveAndFlush方法执行，会清除实体缓存中的count，重置相同key的实体缓存，以及实体缓存对应的资源库查询缓存中的所有缓存条目
 * 
 * @author weiguan
 * @since 0.0.1
 * @date 2022-09-23 14:37
 */
@CacheConfig(
    cacheManager = "redisCacheManager",
    cacheResolver = "redisRepositoryCacheResolver",
    keyGenerator = "repositoryQueryKeyGenerator"
)
@NoRepositoryBean
public interface MicrcJpaRepository<T, I extends IdentityAware> extends Repository<T, I> {

    /**
     * generate identity
     *
     * @param idClass class of identity
     * @return instance of identity with unique id
     * @throws Exception LinkageError if the linkage fails
     *                   ExceptionInInitializerError if the initialization provoked by this method fails
     *                   ClassNotFoundException if the class cannot be located
     *                   NoSuchMethodException if a matching method is not found
     *                   InvocationTargetException if the underlying constructor throws an exception.
     *                   ExceptionInInitializerError if the initialization provoked by this method fails.
     *                   SecurityException
     *                      If a security manager, <i>s</i>, is present and
     *                      the caller's class loader is not the same as or an
     *                      ancestor of the class loader for the current class and
     *                      invocation of {@link SecurityManager#checkPackageAccess
     *                      s.checkPackageAccess()} denies access to the package
     *                      of this class.
     *                   IllegalAccessException
     *                      if this {@code Constructor} object 
     *                      is enforcing Java language access control and the underlying
     *                      constructor is inaccessible.
     *                   IllegalArgumentException
     *                      if the number of actual
     *                      and formal parameters differ; if an unwrapping
     *                      conversion for primitive arguments fails; or if,
     *                      after possible unwrapping, a parameter value
     *                      cannot be converted to the corresponding formal
     *                      parameter type by a method invocation conversion; if
     *                      this constructor pertains to an enum type.
     *                   InstantiationException
     *                      if the class that declares the
     *                      underlying constructor represents an abstract class.
     */
    default I id(Class<I> idClass) throws Exception {
        I idObj = idClass.getConstructor().newInstance();
        idObj.setIdentity(SnowFlakeIdentity.getInstance().nextId());
        return idObj;
    }

    /**
     * generate identity
     *
     * @param idClassName FQCN of identity
     * @return instance of identity with unique id
     * @throws Exception LinkageError if the linkage fails
     *                   ExceptionInInitializerError if the initialization provoked by this method fails
     *                   ClassNotFoundException if the class cannot be located
     */
    @SuppressWarnings("unchecked")
    default I id(String idClassName) throws Exception {
        Class<I> idClass = (Class<I>) Class.forName(idClassName);
        return id(idClass);
    }

    /**
     * find model by id
     *
     * @param id model id
     * @return model
     */
//    @Cacheable(key="#p0.id", sync = true)
    Optional<T> findById(I id);

    /**
     * find models by ids
     *
     * @param ids model ids
     * @return models
     */
    @Cacheable(sync = true)
    List<T> findAllById(Iterable<I> ids);

    /**
     * save model in repo
     *
     * @param <S> model type
     * @param entity saving model
     * @return saved model
     */
    @Caching(
        evict = {
            // TODO 专用generator，参数id为空时，代表新实体，此时才需要驱逐count缓存，其他为更新，返回空字符串为key，不驱逐
            @CacheEvict(keyGenerator = "entityIdKeyGenerator"),
            @CacheEvict(key = "'count'"),
            @CacheEvict(allEntries = true)
        }
    )
    <S extends T> S save(S entity);

    /**
     * model is exists
     *
     * @param id model id
     * @return exists
     */
    @Cacheable(sync = true)
    boolean existsById(I id);

    /**
     * count of models
     *
     * @return count
     */
    @Cacheable(key = "'count'", sync = true)
    long count();

    /**
     * flush session changes
     */
    void flush();

    /**
     * save model and flush
     *
     * @param <S> model type
     * @param entity model for save
     * @return saved model
     */
    @Caching(
        evict = {
            @CacheEvict(keyGenerator = "entityIdKeyGenerator"),
            @CacheEvict(key = "'count'"),
            @CacheEvict(allEntries = true)
        }
    )
    <S extends T> S saveAndFlush(S entity);

    /**
     * find all model pageable
     *
     * @param pageable page and sort param
     * @return models paged
     */
    @Cacheable(sync = true)
    Page<T> findAll(Pageable pageable);

    /**
     * find top one model with example dynamic criteria
     *
     * @param <S> model type
     * @param example example dynamic criteria
     * @return model
     */
    @Cacheable(sync = true)
    <S extends T> Optional<S> findOne(Example<S> example);

    /**
     * find all model with example dynamic criteria pageable
     *
     * @param <S> model type
     * @param example example dynamic criteria
     * @param pageable page and sort param
     * @return models paged
     */
    @Cacheable(sync = true)
    <S extends T> Page<S> findAll(Example<S> example, Pageable pageable);

    /**
     * count of models with example dynamic criteria
     *
     * @param <S> model type
     * @param example example dynamic criteria
     * @return count of models
     */
    @Cacheable(sync = true)
    <S extends T> long count(Example<S> example);

    /**
     * model with example dynamic criteria is exsits
     *
     * @param <S> model type
     * @param example example dynamic criteria
     * @return model exsits
     */
    @Cacheable(sync = true)
    <S extends T> boolean exists(Example<S> example);

    /**
     * delete model
     *
     * @param entity entity
     * @param <S> model type
     */
    <S extends T> void delete(S entity);
}
