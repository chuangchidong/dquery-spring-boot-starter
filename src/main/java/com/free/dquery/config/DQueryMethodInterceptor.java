package com.free.dquery.config;

import com.free.dquery.handle.DQueryHandler;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;

/**
 * @author zhangzhidong
 * @date 2018/6/15
 */
public class DQueryMethodInterceptor implements MethodInterceptor {

    private Logger logger = LoggerFactory.getLogger(DQueryMethodInterceptor.class);

    private SessionFactory sessionFactory;

    public DQueryMethodInterceptor(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        Object[] args = invocation.getArguments();
        logger.info("DQueryMethodInterceptor invoke method {}", method.getName());
        logger.info("DQueryMethodInterceptor invoke method {}", StringUtils.arrayToCommaDelimitedString(args));
        return new DQueryHandler(sessionFactory).handler(method,args);
    }
}
