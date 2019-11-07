# 代码目录结构
![在这里插入图片描述](https://img-blog.csdnimg.cn/20191106101135853.png)

 - rpc-common存放公共类
 - rpc-interface为rpc调用方需要调用的接口
 - rpc-register提供服务的注册与发现
 - rpc-client为rpc调用方底层实现
 - rpc-server为rpc被调用方底层实现
 - rpc-sample-client就是使用自实现的rpc框架调用rpc-sample-server
 - rpc-sample-server就是rpc框架的被调用方

# 技术要点
 ## 1. 使用zookeeper作注册中心，把被调用方的信息注册上去
![在这里插入图片描述](https://img-blog.csdnimg.cn/20191106102722801.png)
![在这里插入图片描述](https://img-blog.csdnimg.cn/20191106102819953.png)
服务的注册
```java
public void register(String data) {
        if (data != null) {
            byte[] bytes = data.getBytes();
            try {
                if (zk.exists(dataPath, null) == null) {
                    zk.create(dataPath, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
                }
                zk.create(dataPath + "/data", bytes, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            } catch (KeeperException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
```

服务的发现
```java
public String discover() {
        String data = null;
        int size = dataList.size();
        // 存在新节点，使用即可
        if (size > 0) {
            if (size == 1) {
                data = dataList.get(0);
            } else {
                data = dataList.get(ThreadLocalRandom.current().nextInt(size));
            }
        }
        return data;
    }
```

## 2、自定义注解
注解RpcService标记被调用方的实现类，RpcClientService标记调用方的类需要生成代理类
```java
@Target({ ElementType.TYPE })//注解用在接口上
@Retention(RetentionPolicy.RUNTIME)//VM将在运行期也保留注释，因此可以通过反射机制读取注解的信息
@Component
public @interface RpcClientService {
}
```

```java
@Target({ ElementType.TYPE })//注解用在接口上
@Retention(RetentionPolicy.RUNTIME)//VM将在运行期也保留注释，因此可以通过反射机制读取注解的信息
@Component
public @interface RpcService {
	Class<?> value();
}
```

## 3、调用方代理类的注入
扫描包下的RpcClientService注解，并生成代理类
```java
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
```

获取代理类
```java
public class RpcProxy<T> implements FactoryBean<T> {

	private String serverAddress;

	private Class<T> interfaceType;

	private ServiceDiscovery serviceDiscovery;

	public RpcProxy(Class<T> interfaceType) {
		this.interfaceType = interfaceType;
	}

	public ServiceDiscovery getServiceDiscovery() {
		return serviceDiscovery;
	}

	public void setServiceDiscovery(ServiceDiscovery serviceDiscovery) {
		this.serviceDiscovery = serviceDiscovery;
	}

	private RpcClient rpcClient;

	@Override
	public T getObject() throws Exception {
		return (T) Proxy.newProxyInstance(interfaceType.getClassLoader(),
				new Class<?>[] { interfaceType }, (proxy, method, args) -> {
					//创建RpcRequest，封装被代理类的属性
					RpcRequest request = new RpcRequest();
					request.setRequestId(UUID.randomUUID().toString());
					//拿到声明这个方法的业务接口名称
					request.setClassName(method.getDeclaringClass()
							.getName());
					request.setMethodName(method.getName());
					request.setParameterTypes(method.getParameterTypes());
					request.setParameters(args);
					synchronized (this) {
						if (rpcClient == null) {
							//查找服务
							if (serviceDiscovery != null) {
								serverAddress = serviceDiscovery.discover();
							}
							//随机获取服务的地址
							String[] array = serverAddress.split(":");
							String host = array[0];
							int port = Integer.parseInt(array[1]);
							//创建Netty实现的RpcClient，链接服务端
							rpcClient = new RpcClient(host, port);
						}
					}
					//通过netty向服务端发送请求
					RpcResponse response = rpcClient.send(request);
					//返回信息
					if (response.isError()) {
						throw response.getError();
					} else {
						return response.getResult();
					}
				});
	}

	@Override
	public Class<?> getObjectType() {
		return interfaceType;
	}

}
```

调用方底层基于netty的发送请求和接收响应
```java
public RpcClient(String host, int port) {
        this.host = host;
        this.port = port;
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group).channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel channel) {
                            // 向pipeline中添加编码、解码、业务处理的handler
                            channel.pipeline()
                                    .addLast(new RpcEncoder(RpcRequest.class))  //OUT - 1
                                    .addLast(new RpcDecoder(RpcResponse.class)) //IN - 1
                                    .addLast(RpcClient.this);                   //IN - 2
                        }
                    }).option(ChannelOption.SO_KEEPALIVE, true);
            // 链接服务器
            future = bootstrap.connect(host, port).sync();
        } catch (Exception e) {
            e.printStackTrace();
            try {
                future.channel().closeFuture().sync();
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            group.shutdownGracefully();
        }
    }

    /**
     * 链接服务端，发送消息
     *
     * @param request
     * @return
     * @throws Exception
     */
    public RpcResponse send(RpcRequest request) throws Exception {
        //将request对象写入outbundle处理后发出（即RpcEncoder编码器）
        // 用线程等待的方式决定是否关闭连接
        // 其意义是：先在此阻塞，等待获取到服务端的返回后，被唤醒，从而关闭网络连接
        Object o = new Object();
        locks.put(request.getRequestId(), o);
        synchronized (o) {
            future.channel().writeAndFlush(request);
            o.wait(10000);
        }
        return response;
    }

    /**
     * 读取服务端的返回结果
     */
    @Override
    public void channelRead0(ChannelHandlerContext ctx, RpcResponse response)
            throws Exception {
        this.response = response;
        Object o = locks.remove(response.getRequestId());
        synchronized (o) {
            o.notify();
        }
    }
```

## 4、被调用方
获取接口与实现类的对应关系
```java
public void setApplicationContext(ApplicationContext ctx)
			throws BeansException {
		Map<String, Object> serviceBeanMap = ctx
				.getBeansWithAnnotation(RpcService.class);
		if (MapUtils.isNotEmpty(serviceBeanMap)) {
			for (Object serviceBean : serviceBeanMap.values()) {
				//从业务实现类上的自定义注解中获取到value，从来获取到业务接口的全名
				String interfaceName = serviceBean.getClass()
						.getAnnotation(RpcService.class).value().getName();
				handlerMap.put(interfaceName, serviceBean);
			}
		}
	}
```

读取调用方传递过来的接口名和参数，利用反射调用相应类并返回结果给前端
```java
public void channelRead0(final ChannelHandlerContext ctx, RpcRequest request)
			throws Exception {
		RpcResponse response = new RpcResponse();
		response.setRequestId(request.getRequestId());
		try {
			//根据request来处理具体的业务调用
			Object result = handle(request);
			response.setResult(result);
		} catch (Throwable t) {
			response.setError(t);
		}
		//写入 outbundle（即RpcEncoder）进行下一步处理（即编码）后发送到channel中给客户端
		ctx.writeAndFlush(response);
	}

	/**
	 * 根据request来处理具体的业务调用
	 * 调用是通过反射的方式来完成
	 * 
	 * @param request
	 * @return
	 * @throws Throwable
	 */
	private Object handle(RpcRequest request) throws Throwable {
		String className = request.getClassName();
		
		//拿到实现类对象
		Object serviceBean = handlerMap.get(className);
		
		//拿到要调用的方法名、参数类型、参数值
		String methodName = request.getMethodName();
		Class<?>[] parameterTypes = request.getParameterTypes();
		Object[] parameters = request.getParameters();
		
		//拿到接口类
		Class<?> forName = Class.forName(className);
		
		//调用实现类对象的指定方法并返回结果
		Method method = forName.getMethod(methodName, parameterTypes);
		return method.invoke(serviceBean, parameters);
	}
```

## 5、自定义rpc框架的使用
### 1、被调用方maven依赖
```xml
<dependency>
            <groupId>com.shuangyueliao</groupId>
            <artifactId>rpc-server</artifactId>
            <version>1.0-SNAPSHOT</version>
</dependency>
```
### 2、调用方maven依赖
```xml
<dependency>
            <groupId>com.shuangyueliao</groupId>
            <artifactId>rpc-client</artifactId>
            <version>1.0-SNAPSHOT</version>
            <scope>compile</scope>
</dependency>
```
### 3、被调用方实现类加上注解RpcService，里面的值是被调用的接口
```java
@RpcService(PayService.class)
public class PayServiceImpl implements PayService {
    @Override
    public int calculate(int a, int b) {
        int result = a + b;
        return result;
    }
}
```
### 4、调用方建立包名com.shuangyueliao.rpc.myinterface，新建要调用的接口，并加上注解RpcClientService
```java
@RpcClientService
public interface PayService {
    int calculate(int a, int b);
}
```

# 功能演示
1、启动zookeeper，如需要修改zookeeper连接地址，请修改rpc-sample-server和rpc-sample-client的配置文件application.properties中的配置项zookeeper.url  
2、运行rpc-sample-server(被调用方)RpcBootstrap的main方法启动被调用方  
3、运行rpc-sample-client(调用方)的StartApp的main方法启动调用方  
4、浏览器输入http://localhost:8090/order请求rpc-sample-client，rpc-sample-client会RPC调用rpc-sample-server  
![在这里插入图片描述](https://img-blog.csdnimg.cn/2019110714461536.png)
![在这里插入图片描述](https://img-blog.csdnimg.cn/20191107144814876.png)

  
[项目地址](https://github.com/shuangyueliao/rpc)
