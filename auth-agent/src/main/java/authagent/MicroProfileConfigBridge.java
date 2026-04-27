package authagent;

import io.a2a.server.requesthandlers.DefaultRequestHandler;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Bridges MicroProfile @ConfigProperty injection for DefaultRequestHandler so it works
 * in a Spring Boot context. Spring understands @Inject but not @ConfigProperty, causing
 * "No qualifying bean of type 'int'" errors. This post-processor sets those fields from
 * Spring's Environment and returns false to skip Spring's own property population step.
 *
 * Uses EnvironmentAware (not constructor/field injection) because BeanPostProcessor beans
 * are instantiated before AutowiredAnnotationBeanPostProcessor is ready.
 */
@Component
public class MicroProfileConfigBridge
    implements InstantiationAwareBeanPostProcessor, PriorityOrdered, EnvironmentAware {

  private Environment environment;

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }

  @Override
  public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
    if (bean instanceof DefaultRequestHandler) {
      injectConfigProperties(bean);
      return false; // skip Spring's @Inject / @Autowired population for this bean
    }
    return true;
  }

  private void injectConfigProperties(Object bean) {
    for (Field field : allFields(bean.getClass())) {
      ConfigProperty cp = field.getAnnotation(ConfigProperty.class);
      if (cp == null) continue;
      String raw = environment.getProperty(cp.name(), cp.defaultValue());
      try {
        field.setAccessible(true);
        Class<?> type = field.getType();
        if (type == int.class || type == Integer.class) {
          field.set(bean, Integer.parseInt(raw));
        } else if (type == long.class || type == Long.class) {
          field.set(bean, Long.parseLong(raw));
        } else if (type == boolean.class || type == Boolean.class) {
          field.set(bean, Boolean.parseBoolean(raw));
        } else if (type == String.class) {
          field.set(bean, raw);
        }
      } catch (Exception e) {
        throw new RuntimeException(
            "Failed to inject @ConfigProperty '" + cp.name() + "' into " + field, e);
      }
    }
  }

  private static List<Field> allFields(Class<?> clazz) {
    List<Field> fields = new ArrayList<>();
    for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
      for (Field f : c.getDeclaredFields()) {
        fields.add(f);
      }
    }
    return fields;
  }
}
