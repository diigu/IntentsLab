package com.github.michalbednarski.intentslab.valueeditors.object;

import com.github.michalbednarski.intentslab.FormattedTextBuilder;
import com.github.michalbednarski.intentslab.Utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.regex.Pattern;

import static com.github.michalbednarski.intentslab.FormattedTextBuilder.ValueSemantic;

/**
 * Class invoking getters of object and returning summary as CharSequence
 */
public class GettersInvoker {
    private static final Pattern GETTER_PATTERN = Pattern.compile("(?!getClass$)(is|get)[A-Z].*");

    private Object mObject;
    private Method[] mGetters;


    public GettersInvoker(Object object) {
        final Class<?> aClass = object.getClass();
        ArrayList<Method> getters = new ArrayList<Method>();
        for (Method method : aClass.getMethods()) {
            if (
                    GETTER_PATTERN.matcher(method.getName()).matches() && // method name looks like getter
                            (method.getModifiers() & Modifier.STATIC) == 0 && // method isn't static
                            method.getReturnType() != Void.TYPE && // method has return value
                            method.getParameterTypes().length == 0 // method has no arguments
                    ) {
                getters.add(method);
            }
        }

        mObject = object;
        mGetters = getters.toArray(new Method[getters.size()]);
    }

    /**
     * Invoke all getters and return summary of their results as formatted text
     */
    public CharSequence getGettersValues() {
        FormattedTextBuilder ftb = new FormattedTextBuilder();
        for (Method method : mGetters) {
            ValueSemantic valueSemantic = ValueSemantic.NONE;
            String value;
            try {

                // Invoke getter method
                Object o = method.invoke(mObject);

                // Use "null" if result is null
                value = o == null ? "null" : o.toString();

            } catch (InvocationTargetException wrappedException) {
                // Getter method thrown exception,
                // Unwrap it and display as method result
                final Throwable targetException = wrappedException.getTargetException();
                assert targetException != null;
                value = Utils.describeException(targetException);
                valueSemantic = ValueSemantic.ERROR;

            } catch (IllegalAccessException e) {
                // Shouldn't happen, non-public methods are excluded in onCreate
                throw new RuntimeException("Accessor method not accessible", e);
            }
            ftb.appendValue(method.getName() + "()", value, false, valueSemantic);
        }

        // Show results in EditText
        return ftb.getText();
    }

    public boolean gettersExist() {
        return mGetters.length != 0;
    }
}
