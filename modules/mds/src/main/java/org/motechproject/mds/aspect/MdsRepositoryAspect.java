package org.motechproject.mds.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.motechproject.mds.repository.BaseMdsRepository;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class MdsRepositoryAspect {

    @Around("within(org.motechproject.mds.repository.All*)")
    public Object changeClassLoader(ProceedingJoinPoint joinPoint) throws Throwable { // NO CHECKSTYLE IllegalThrowsCheck
        Object target = joinPoint.getTarget();

        if (!(target instanceof BaseMdsRepository)) {
            throw new IllegalStateException(
                    "The target class should extend " + BaseMdsRepository.class.getName()
            );
        }

        ClassLoader webAppClassLoader = Thread.currentThread().getContextClassLoader();

        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            return joinPoint.proceed();
        } finally {
            Thread.currentThread().setContextClassLoader(webAppClassLoader);
        }
    }
}
