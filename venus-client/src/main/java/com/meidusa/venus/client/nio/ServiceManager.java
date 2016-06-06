package com.meidusa.venus.client.nio;

import com.meidusa.toolkit.common.runtime.GlobalScheduler;
import com.meidusa.toolkit.net.ConnectionConnector;
import com.meidusa.toolkit.net.ConnectionManager;
import com.meidusa.venus.client.VenusNIOMessageHandler;
import com.meidusa.venus.client.nio.config.ServiceConfig;
import com.meidusa.venus.exception.DefaultVenusException;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import javax.annotation.PostConstruct;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by huawei on 5/17/16.
 */
public class ServiceManager implements InitializingBean, BeanFactoryPostProcessor {

    private String refRegistryId;
    private List<ServiceConfig> serviceConfigList;

    public ScheduledExecutorService executors;

    private Map<Class<?>, Object> servicesMap = new HashMap<Class<?>, Object>();


    private ConnectionConnector connector;
    private ConnectionManager connManager;
    private VenusNIOMessageHandler messageHandler = new VenusNIOMessageHandler();
    private int asyncExecutorSize = 10;


    @Override
    public void afterPropertiesSet() throws Exception{
        if (serviceConfigList != null && serviceConfigList.size() >0) {
            executors = Executors.newScheduledThreadPool(serviceConfigList.size());
        }else {
            executors = Executors.newScheduledThreadPool(10);
        }

        this.connector = new ConnectionConnector("connection Connector");
        connector.setDaemon(true);

        connManager = new ConnectionManager("Connection Manager", asyncExecutorSize);
        connManager.setDaemon(true);
        connManager.start();

        connector.setProcessors(new ConnectionManager[] { connManager });
        connector.start();
    }




    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        RegistryManager rm;

        try{
            rm = beanFactory.getBean(refRegistryId, RegistryManager.class);
        }catch (Exception e) {
            throw new NoSuchBeanDefinitionException("get " + refRegistryId + " bean error, check venus:registry configuration or has multiple id are same");
        }

        if (rm == null) {
            throw new DefaultVenusException(0, "注册中心管理器不能为空");
        }

        if (serviceConfigList == null) {
            return;
        }

        for(ServiceConfig config : serviceConfigList) {
            NioServiceInvocationHandler handler = null;
            try {
                handler = new NioServiceInvocationHandler(rm, config, this.connector).init();
            } catch (Exception e) {
                e.printStackTrace();
            }
            Object remoteServiceInstance = Proxy.newProxyInstance(this.getClass().getClassLoader(), new Class[]{config.getServiceInterface()}, handler);
            servicesMap.put(config.getServiceInterface(), remoteServiceInstance);
            beanFactory.registerSingleton(config.getServiceName(), remoteServiceInstance);
        }

    }

    public List<ServiceConfig> getServiceConfigList() {
        return serviceConfigList;
    }

    public void setServiceConfigList(List<ServiceConfig> serviceConfigList) {
        this.serviceConfigList = serviceConfigList;
    }

    public String getRefRegistryId() {
        return refRegistryId;
    }

    public void setRefRegistryId(String refRegistryId) {
        this.refRegistryId = refRegistryId;
    }

    public <T> T getService(Class serviceClass) {
        if (serviceClass == null) {
            return null;
        }
        return (T) servicesMap.get(serviceClass);
    }
}