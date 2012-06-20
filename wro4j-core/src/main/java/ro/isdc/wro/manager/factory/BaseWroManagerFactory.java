/*
 * Copyright (c) 2008. All rights reserved.
 */
package ro.isdc.wro.manager.factory;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.isdc.wro.WroRuntimeException;
import ro.isdc.wro.cache.CacheEntry;
import ro.isdc.wro.cache.CacheStrategy;
import ro.isdc.wro.cache.ContentHashEntry;
import ro.isdc.wro.cache.impl.LruMemoryCacheStrategy;
import ro.isdc.wro.manager.WroManager;
import ro.isdc.wro.model.WroModel;
import ro.isdc.wro.model.factory.WroModelFactory;
import ro.isdc.wro.model.factory.XmlModelFactory;
import ro.isdc.wro.model.group.DefaultGroupExtractor;
import ro.isdc.wro.model.group.GroupExtractor;
import ro.isdc.wro.model.group.processor.Injector;
import ro.isdc.wro.model.group.processor.InjectorBuilder;
import ro.isdc.wro.model.resource.locator.factory.DefaultUriLocatorFactory;
import ro.isdc.wro.model.resource.locator.factory.UriLocatorFactory;
import ro.isdc.wro.model.resource.processor.factory.DefaultProcesorsFactory;
import ro.isdc.wro.model.resource.processor.factory.ProcessorsFactory;
import ro.isdc.wro.model.resource.support.hash.HashStrategy;
import ro.isdc.wro.model.resource.support.hash.SHA1HashStrategy;
import ro.isdc.wro.model.resource.support.naming.NamingStrategy;
import ro.isdc.wro.model.resource.support.naming.NoOpNamingStrategy;
import ro.isdc.wro.model.transformer.WildcardExpanderModelTransformer;
import ro.isdc.wro.util.DestroyableLazyInitializer;
import ro.isdc.wro.util.Transformer;


/**
 * Default implementation of {@link WroManagerFactory} which creates default locators and processors and handles the
 * injection logic by creating an {@link Injector} and injecting where it is appropriate.
 *
 * @author Alex Objelean
 * @created Created on Dec 30, 2009
 */
public class BaseWroManagerFactory
  implements WroManagerFactory {
  private static final Logger LOG = LoggerFactory.getLogger(BaseWroManagerFactory.class);

  private GroupExtractor groupExtractor;
  private WroModelFactory modelFactory;
  private CacheStrategy<CacheEntry, ContentHashEntry> cacheStrategy;
  private HashStrategy hashStrategy;
  /**
   * A list of model transformers. Allows manager to mutate the model before it is being parsed and
   * processed.
   */
  private List<Transformer<WroModel>> modelTransformers;
  private UriLocatorFactory uriLocatorFactory;
  private ProcessorsFactory processorsFactory;
  private NamingStrategy namingStrategy;
  /**
   * Handles the lazy synchronized creation of the manager
   */
  private DestroyableLazyInitializer<WroManager> managerInitializer = new DestroyableLazyInitializer<WroManager>() {
    @Override
    protected WroManager initialize() {
      final WroManager manager = new WroManager();
      if (modelFactory == null) {
        modelFactory = newModelFactory();
      }
      if (groupExtractor == null) {
        groupExtractor = newGroupExtractor();
      }
      if (cacheStrategy == null) {
        cacheStrategy = newCacheStrategy();
      }
      if (hashStrategy == null) {
        hashStrategy = newHashStrategy();
      }
      if (modelTransformers == null) {
        modelTransformers = newModelTransformers();
      }
      if (processorsFactory == null) {
        processorsFactory = newProcessorsFactory();
      }
      if (uriLocatorFactory == null) {
        uriLocatorFactory = newUriLocatorFactory();
      }
      //use NoOp as default naming strategy
      if (namingStrategy == null) {
        namingStrategy = newNamingStrategy();
      }

      manager.setGroupExtractor(groupExtractor);
      manager.setCacheStrategy(cacheStrategy);
      manager.setHashBuilder(hashStrategy);
      manager.setUriLocatorFactory(uriLocatorFactory);
      manager.setProcessorsFactory(processorsFactory);
      manager.setNamingStrategy(namingStrategy);
      manager.setModelFactory(modelFactory);
      manager.setModelTransformers(modelTransformers);

      
      final Injector injector = new InjectorBuilder(manager).build();
      injector.inject(manager);
      
      //initialize before injection to allow injector do its job properly
      onAfterInitializeManager(manager);
      
      return manager;
    }
  };

  /**
   * Creates default singleton instance of manager, by initializing manager dependencies with default values
   * (processors).
   */
  public final WroManager create() {
    return managerInitializer.get();
  }

  /**
   * Allows factory to do additional manager configuration after it was initialzed. One use-case is to configure
   * callbacks. Default implementation does nothing. Do not set anything else except callbacks in this method, otherwise
   * the initialization will not be performed properly.
   * 
   * @param manager
   *          initialized instance of {@link WroManager}.
   */
  protected void onAfterInitializeManager(final WroManager manager) {
  }

  /**
   * @param namingStrategy the namingStrategy to set
   */
  public BaseWroManagerFactory setNamingStrategy(final NamingStrategy namingStrategy) {
    this.namingStrategy = namingStrategy;
    return this;
  }


  /**
   * @return the namingStrategy
   */
  public NamingStrategy getNamingStrategy() {
    return namingStrategy;
  }
  
  /**
   * @return default implementation of modelTransformers.
   */
  protected List<Transformer<WroModel>> newModelTransformers() {
    addModelTransformer(new WildcardExpanderModelTransformer());
    return this.modelTransformers;
  }


  /**
   * Override to provide a different or modified default factory implementation.
   *
   * @return {@link ProcessorsFactory} object.
   */
  protected ProcessorsFactory newProcessorsFactory() {
    return new DefaultProcesorsFactory();
  }


  /**
   * Override to provide a different or modified factory.
   *
   * @return {@link UriLocatorFactory} object.
   */
  protected UriLocatorFactory newUriLocatorFactory() {
    return new DefaultUriLocatorFactory();
  }


  /**
   * @return {@link HashStrategy} instance.
   */
  protected HashStrategy newHashStrategy() {
    return new SHA1HashStrategy();
  }
  
  /**
   * @return default {@link NamingStrategy} to be used by this {@link WroManagerFactory} 
   */
  protected NamingStrategy newNamingStrategy() {
    return new NoOpNamingStrategy();
  }

  /**
   * {@inheritDoc}
   */
  public void onCachePeriodChanged(final long period) {
    try {
      managerInitializer.get().onCachePeriodChanged(period);
    } catch (final WroRuntimeException e) {
      LOG.warn("[FAIL] Unable to reload cache, probably because invoked outside of context");
    }
  }


  /**
   * {@inheritDoc}
   */
  public void onModelPeriodChanged(final long period) {
    try {
      managerInitializer.get().onModelPeriodChanged(period);
      // update cache too.
      managerInitializer.get().getCacheStrategy().clear();
    } catch (final WroRuntimeException e) {
      LOG.warn("[FAIL] Unable to reload model, probably because invoked outside of context");
    }
  }


  /**
   * @return {@link CacheStrategy} instance for resources' group caching.
   */
  protected CacheStrategy<CacheEntry, ContentHashEntry> newCacheStrategy() {
    return new LruMemoryCacheStrategy<CacheEntry, ContentHashEntry>();
  }


  /**
   * @return {@link GroupExtractor} implementation.
   */
  protected GroupExtractor newGroupExtractor() {
    return new DefaultGroupExtractor();
  }


  /**
   * @param servletContext {@link ServletContext} which could be useful for creating dynamic {@link WroModel}.
   * @return {@link WroModelFactory} implementation
   */
  protected WroModelFactory newModelFactory() {
    try {
      LOG.debug("Trying to use SmartWroModelFactory as default model factory");
      final Class<? extends WroModelFactory> smartFactoryClass = Class.forName(
        "ro.isdc.wro.extensions.model.factory.SmartWroModelFactory").asSubclass(WroModelFactory.class);
      return smartFactoryClass.newInstance();
    } catch (final Exception e) {
      LOG.info("[FAIL] SmartWroModelFactory is not available. Using default model factory.");
      LOG.debug("Reason: {}", e.toString());
    }
    return new XmlModelFactory();
  }


  /**
   * @param groupExtractor the groupExtractor to set
   */
  public BaseWroManagerFactory setGroupExtractor(final GroupExtractor groupExtractor) {
    this.groupExtractor = groupExtractor;
    return this;
  }


  /**
   * @param modelFactory the modelFactory to set
   */
  public BaseWroManagerFactory setModelFactory(final WroModelFactory modelFactory) {
    this.modelFactory = modelFactory;
    return this;
  }


  /**
   * @param hashBuilder the hashBuilder to set
   */
  public BaseWroManagerFactory setHashBuilder(final HashStrategy hashBuilder) {
    this.hashStrategy = hashBuilder;
    return this;
  }


  /**
   * @param modelTransformers the modelTransformers to set
   */
  public BaseWroManagerFactory setModelTransformers(final List<Transformer<WroModel>> modelTransformers) {
    this.modelTransformers = modelTransformers;
    return this;
  }

  /**
   * Add a single model transformer.
   */
  public BaseWroManagerFactory addModelTransformer(final Transformer<WroModel> modelTransformer) {
    if (modelTransformers == null) {
      modelTransformers = new ArrayList<Transformer<WroModel>>();
    }
    this.modelTransformers.add(modelTransformer);
    return this;
  }

  /**
   * @param cacheStrategy the cacheStrategy to set
   */
  public BaseWroManagerFactory setCacheStrategy(final CacheStrategy<CacheEntry, ContentHashEntry> cacheStrategy) {
    this.cacheStrategy = cacheStrategy;
    return this;
  }


  /**
   * @param uriLocatorFactory the uriLocatorFactory to set
   */
  public BaseWroManagerFactory setUriLocatorFactory(final UriLocatorFactory uriLocatorFactory) {
    this.uriLocatorFactory = uriLocatorFactory;
    return this;
  }

  /**
   * @param processorsFactory the processorsFactory to set
   */
  public void setProcessorsFactory(final ProcessorsFactory processorsFactory) {
    this.processorsFactory = processorsFactory;
  }
  

  public WroModelFactory getModelFactory() {
    return modelFactory;
  }

  /**
   * {@inheritDoc}
   */
  public void destroy() {
    managerInitializer.destroy();
  }
}
