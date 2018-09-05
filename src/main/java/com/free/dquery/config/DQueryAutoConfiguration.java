package com.free.dquery.config;

import com.free.dquery.annotation.DQuery;
import org.aopalliance.aop.Advice;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.vendor.HibernateJpaSessionFactoryBean;

import javax.annotation.PostConstruct;

/**
 * @author zhangzhidong
 * @date 2018/6/13
 */
@Configuration
public class DQueryAutoConfiguration extends AbstractPointcutAdvisor {

    private Logger logger = LoggerFactory.getLogger(DQueryAutoConfiguration.class);

    private Pointcut pointcut;

    private Advice advice;

    @Autowired
    private SessionFactory sessionFactory;

    @Bean
    public HibernateJpaSessionFactoryBean sessionFactory() {
        return new HibernateJpaSessionFactoryBean();
    }

    @PostConstruct
    public void init() {
        logger.info("init DQueryAutoConfiguration start");
        this.pointcut = new AnnotationMatchingPointcut(null, DQuery.class);
        this.advice = new DQueryMethodInterceptor(sessionFactory);
        logger.info("init DQueryAutoConfiguration end");
    }

    @Override
    public Pointcut getPointcut() {
        return this.pointcut;
    }

    @Override
    public Advice getAdvice() {
        return this.advice;
    }

}

