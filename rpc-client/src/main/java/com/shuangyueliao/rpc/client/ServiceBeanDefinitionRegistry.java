package com.shuangyueliao.rpc.client;


import com.shuangyueliao.rpc.common.RpcClientService;
import com.shuangyueliao.rpc.register.ServiceDiscovery;
import org.reflections.Reflections;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Set;

/**
 * 用于Spring动态注入自定义接口
 *
 * @author shuangyueliao
 */
@Component
public class ServiceBeanDefinitionRegistry implements BeanDefinitionRegistryPostProcessor {
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        Set<Class<?>> typesAnnotatedWith = new Reflections("com.shuangyueliao.rpc.myinterface").getTypesAnnotatedWith(RpcClientService.class);
        for (Class beanClazz : typesAnnotatedWith) {
            BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(beanClazz);
            GenericBeanDefinition definition = (GenericBeanDefinition) builder.getRawBeanDefinition();

            //在这里，我们可以给该对象的属性注入对应的实例。
            //比如mybatis，就在这里注入了dataSource和sqlSessionFactory，
            // 注意，如果采用definition.getPropertyValues()方式的话，
            // 类似definition.getPropertyValues().add("interfaceType", beanClazz);
            // 则要求在FactoryBean（本应用中即ServiceFactory）提供setter方法，否则会注入失败
            // 如果采用definition.getConstructorArgumentValues()，
            // 则FactoryBean中需要提供包含该属性的构造方法，否则会注入失败
            Properties properties = new Properties();
            InputStream is=this.getClass().getResourceAsStream("/application.properties");
            try {
                properties.load(is);
            } catch (IOException e) {
                e.printStackTrace();
            }
            String registerAddress = properties.getProperty("zookeeper.url");
            String dataPath = properties.getProperty("zookeeper.register.path.prefix");
            ServiceDiscovery serviceDiscovery = new ServiceDiscovery(registerAddress, dataPath);
            definition.getPropertyValues().addPropertyValue("serviceDiscovery", serviceDiscovery);
            definition.getConstructorArgumentValues().addGenericArgumentValue(beanClazz);

            //注意，这里的BeanClass是生成Bean实例的工厂，不是Bean本身。
            // FactoryBean是一种特殊的Bean，其返回的对象不是指定类的一个实例，
            // 其返回的是该工厂Bean的getObject方法所返回的对象。
            definition.setBeanClass(RpcProxy.class);

            //这里采用的是byType方式注入，类似的还有byName等
            definition.setAutowireMode(GenericBeanDefinition.AUTOWIRE_BY_TYPE);
            registry.registerBeanDefinition(beanClazz.getSimpleName(), definition);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

}
