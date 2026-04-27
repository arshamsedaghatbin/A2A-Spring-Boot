package memoryagent;

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

@Component
public class MicroProfileConfigBridge
    implements InstantiationAwareBeanPostProcessor, PriorityOrdered, EnvironmentAware {

  private Environment environment;

  @Override public void setEnvironment(Environment environment) { this.environment = environment; }
  @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }

  @Override
  public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
    if (bean instanceof DefaultRequestHandler) {
      injectConfigProperties(bean);
      return false;
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
        if      (type == int.class     || type == Integer.class) field.set(bean, Integer.parseInt(raw));
        else if (type == long.class    || type == Long.class)    field.set(bean, Long.parseLong(raw));
        else if (type == boolean.class || type == Boolean.class) field.set(bean, Boolean.parseBoolean(raw));
        else if (type == String.class)                           field.set(bean, raw);
      } catch (Exception e) {
        throw new RuntimeException("Failed to inject @ConfigProperty '" + cp.name() + "'", e);
      }
    }
  }

  private static List<Field> allFields(Class<?> clazz) {
    List<Field> fields = new ArrayList<>();
    for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass())
      for (Field f : c.getDeclaredFields()) fields.add(f);
    return fields;
  }
}
