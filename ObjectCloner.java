import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for cloning objects.
 *
 * @author Roberto Navarro
 */
public class ObjectCloner {

	/**
	 * Caches class default constructors.
	 */
	private static final Map<Class, Constructor> classConstructors = new HashMap<>();

	/**
	 * Caches which classes are immutable.
	 */
	private static final Map<Class, Boolean> immutableClasses = new HashMap<>();

	/**
	 * Caches declared fields for class.
	 */
	private static final Map<Class, List<Field>> declaredFields = new HashMap<>();

	static {
		immutableClasses.put(BigDecimal.class, true);
		immutableClasses.put(BigInteger.class, true);
		immutableClasses.put(Boolean.class, true);
		immutableClasses.put(Byte.class, true);
		immutableClasses.put(Character.class, true);
		immutableClasses.put(Double.class, true);
		immutableClasses.put(Float.class, true);
		immutableClasses.put(Integer.class, true);
		immutableClasses.put(Long.class, true);
		immutableClasses.put(Short.class, true);
		immutableClasses.put(String.class, true);
	}

	private ObjectCloner() {
		// Hide constructor.
	}

	/**
	 * Creates a deep copy of object.
	 *
	 * @param object object to copy
	 * @return copied object, throws InstantiationException if any object in the tree does not have default constructor
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws InvocationTargetException
	 */
	public static <T> T deepClone(final T object)
			throws IllegalAccessException, InstantiationException, InvocationTargetException {
		if (object == null) {
			return null;
		}
		return doClone(object, new HashMap<>(), Integer.MAX_VALUE);
	}

	/**
	 * Creates a shallow copy of object.
	 *
	 * @param object object to copy.
	 * @return copied object, throws InstantiationException if any object in the tree does not have default constructor
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws InvocationTargetException
	 */
	public static <T> T shallowClone(final T object)
			throws IllegalAccessException, InstantiationException, InvocationTargetException {
		if (object == null) {
			return null;
		}
		return doClone(object, new HashMap<>(), 2);
	}

	/**
	 * Clones object recursively.
	 *
	 * @param object object to clone
	 * @param visited objects already visited
	 * @param maxDeep max valid cloning deep
	 * @return
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws InvocationTargetException
	 */
	@SuppressWarnings("unchecked")
	private static <T> T doClone(final T object, final Map<Object, Object> visited, int maxDeep)
			throws IllegalAccessException, InstantiationException, InvocationTargetException {
		if (object == null || maxDeep == 0) {
			return null;
		}
		final Class clazz = object.getClass();
		if (isImmutable(clazz)) {
			return object;
		}
		T clone = (T) visited.get(object);
		if (clone == null) {
			clone = newInstance(clazz);
			visited.put(object, clone);
			for (Field field : getDeclaredFields(clazz)) {
				cloneField(object, clone, field, visited, maxDeep);
			}
		}
		return clone;
	}

	/**
	 * Clones object field.
	 *
	 * @param object source object
	 * @param clone cloned object
	 * @param field field to process
	 * @param visited objects already visited
	 * @param maxDeep max valid cloning deep
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws InvocationTargetException
	 */
	private static <T> void cloneField(final T object, final T clone, final Field field, final Map<Object, Object> visited, final int maxDeep)
			throws IllegalAccessException, InstantiationException, InvocationTargetException {
		if ((field.getModifiers() & Modifier.STATIC) != 0) {
			return;
		}

		if (field.getType().isPrimitive()) {
			field.set(clone, field.get(object));
		} else if (field.getType().isArray()) {
			if (field.getType().isAssignableFrom(Object[].class)) {
				field.set(clone, cloneObjectArray((Object[]) field.get(object), visited, maxDeep));
			} else {
				field.set(clone, clonePrimitiveArray(field.get(object)));
			}
		} else {
			field.set(clone, doClone(field.get(object), visited, maxDeep - 1));
		}
	}

	@SuppressWarnings("unchecked")
	private static Object clonePrimitiveArray(Object array) {
		if (array == null) {
			return null;
		}

		final int length = Array.getLength(array);
		final Object clone = Array.newInstance(array.getClass().getComponentType(), length);
		System.arraycopy(array, 0, clone, 0, length);
		return clone;
	}

	/**
	 * Clones Object[] object.
	 *
	 * @param array array to clone
	 * @param visited objects already visited
	 * @param maxDeep max valid cloning deep
	 * @return
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws InvocationTargetException
	 */
	private static Object[] cloneObjectArray(Object[] array, final Map<Object, Object> visited, final int maxDeep)
			throws IllegalAccessException, InstantiationException, InvocationTargetException {
		if (array == null) {
			return null;
		}

		final Object[] clone = new Object[array.length];
		for (int i = 0; i < array.length; i++) {
			clone[i] = doClone(array[i], visited, maxDeep);
		}
		return clone;
	}

	/**
	 * Creates new instance of class.
	 *
	 * @param clazz class for new instance
	 * @return new instance or InstantiationException if there are not valid constructor
	 * @throws IllegalAccessException
	 * @throws InstantiationException
	 * @throws InvocationTargetException
	 */
	@SuppressWarnings("unchecked")
	private static <T> T newInstance(final Class clazz)
			throws IllegalAccessException, InstantiationException, InvocationTargetException {
		final Constructor constructor = getConstructor(clazz);
		if (constructor == null) {
			throw new InstantiationException("Cannot create new instance for " + clazz);
		}
		return (T) constructor.newInstance();
	}

	/**
	 * Class.newInstance() works only for classes with default constructor.
	 *
	 * @param clazz class object.
	 * @return constructor for the class.
	 */
	private static Constructor getConstructor(final Class clazz) {
		if (classConstructors.containsKey(clazz)) {
			return classConstructors.get(clazz);
		}
		Constructor constructor = null;
		for (Constructor c : clazz.getConstructors()) {
			if (c.getGenericParameterTypes().length == 0) {
				c.setAccessible(true);
				constructor = c;
				break;
			}
		}
		synchronized (classConstructors) {
			classConstructors.put(clazz, constructor);
		}
		return constructor;
	}

	/**
	 * Checks if a class is immutable, caching the result.
	 *
	 * @param clazz class to check
	 * @return true is class is immutable
	 */
	private static boolean isImmutable(final Class clazz) {
		Boolean isImmutable = immutableClasses.get(clazz);
		if (isImmutable == null) {
			isImmutable = checkClassImmutability(clazz);
			synchronized (immutableClasses) {
				immutableClasses.put(clazz, isImmutable);
			}
		}
		return isImmutable;
	}

	/**
	 * Checks if a class is immutable, a class is immutable when:
	 * - all its fields are private and final
	 * - non primitive fields are also immutable
	 *
	 * @param clazz class to check
	 * @return true if class is immutable
	 */
	private static boolean checkClassImmutability(final Class clazz) {
		for (Field field : getDeclaredFields(clazz)) {
			final int modifiers = field.getModifiers();
			if ((modifiers & Modifier.PRIVATE) == 0 || (modifiers & Modifier.FINAL) == 0
					|| (!field.getType().isPrimitive() && !isImmutable(field.getType()))) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Retrieves all fields for a class, setting them as accesible if they are default, protected or private.
	 *
	 * @param clazz class to inspect
	 * @return list of fields
	 */
	private static List<Field> getDeclaredFields(final Class clazz) {
		List<Field> fields = declaredFields.get(clazz);
		if (fields == null) {
			fields = new ArrayList<>();
			Class c = clazz;
			do {
				for (Field field : c.getDeclaredFields()) {
					if ((field.getModifiers() & Modifier.STATIC) == 0) {
						field.setAccessible(true);
						fields.add(field);
					}
				}
			} while ((c = c.getSuperclass()) != null);
			synchronized (declaredFields) {
				declaredFields.put(clazz, fields);
			}
		}
		return fields;
	}

}
