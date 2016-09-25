package info.leadinglight.umljavadoclet.model;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.ParameterizedType;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a class internal or external to the model.
 */
public class ModelClass {
    public ModelClass(Model model, Type type) {
        _model = model;
        _type = type;
        _classDoc = _type.asClassDoc();
    }
    
    public enum Visibility {
        PUBLIC, PROTECTED, PRIVATE, PACKAGE_PRIVATE
    }
    
    public enum ClassType {
        INTERFACE, ENUM, CLASS
    }
    
    public static class Field {
        public Field(String name, String type, Visibility visibility, boolean isStatic) {
            this.name = name;
            this.type = type;
            this.visibility = visibility;
            this.isStatic = isStatic;
        }
        
        public String name;
        public String type;
        public Visibility visibility;
        public boolean isStatic;
    }
    
    public static class Constructor {
        public Constructor(String name, List<MethodParameter> parameters, Visibility visibility) {
            this.name = name;
            this.parameters = parameters;
            this.visibility = visibility;
        }
        
        public String name;
        public List<MethodParameter> parameters;
        public Visibility visibility;
    }
    
    public static class Method {
        public Method(String name, List<MethodParameter> parameters, String returnType, Visibility visibility, boolean isAbstract, boolean isStatic) {
            this.name = name;
            this.parameters = parameters;
            this.returnType = returnType;
            this.visibility = visibility;
            this.isAbstract = isAbstract;
            this.isStatic = isStatic;
        }
        
        public String name;
        public List<MethodParameter> parameters;
        public String returnType;
        public Visibility visibility;
        public boolean isAbstract;
        public boolean isStatic;
    }
    
    public static class MethodParameter {
        public MethodParameter(String type, String name) {
            this.type = type;
            this.name = name;
        }
        
        public String type;
        public String name;
    }
    
    public void map() {
        if (isInternal()) {
            // Only map internal classes.
            mapRelationships();
        }
    }
    
    public String fullName() {
        return fullName(_type);
    }
    
    public String qualifiedName() {
        return _type.qualifiedTypeName();
    }
    
    public ClassType type() {
        if (_classDoc.isInterface()) {
            return ClassType.INTERFACE;
        } else if (_classDoc.isEnum()) {
            return ClassType.ENUM;
        } else {
            return ClassType.CLASS;
        }
    }
    
    public boolean isInternal() {
        return _classDoc != null ? _classDoc.isIncluded() : false;
    }
    
    public boolean isExternal() {
        return !isInternal();
    }
    
    public List<ModelRel> relationships() {
        return _rels;
    }
    
    public RelFilter relationshipsFilter() {
        return new RelFilter(_rels);
    }
    
    public ModelClass superclass() {
        ModelRel rel = relationshipsFilter().source(this).kind(ModelRel.Kind.GENERALIZATION).first();
        return rel != null ? rel.destination() : null;
    }
    
    public List<ModelClass> interfaces() {
        return relationshipsFilter().source(this).kind(ModelRel.Kind.REALIZATION).destinationClasses();
    }
    
    public List<ModelRel> sourceAssociations() {
        return relationshipsFilter().source(this).kind(ModelRel.Kind.DIRECTED_ASSOCIATION).all();
    }
    
    public List<ModelRel> destinationAssociations() {
        return relationshipsFilter().destination(this).kind(ModelRel.Kind.DIRECTED_ASSOCIATION).all();
    }

    public List<ModelClass> dependencies() {
        return relationshipsFilter().source(this).kind(ModelRel.Kind.DEPENDENCY).destinationClasses();
    }
    
    public List<ModelClass> dependents() {
        return relationshipsFilter().destination(this).kind(ModelRel.Kind.DEPENDENCY).sourceClasses();
    }
    
    public boolean hasRelationshipWith(ModelClass dest) {
        return relationshipsFilter().source(this).destination(dest).first() != null;
    }
    
    public List<Field> fields() {
        List<Field> fields = new ArrayList<>();
        for (FieldDoc fieldDoc: _classDoc.fields(false)) {
            Field mappedField = new Field(fieldDoc.name(), shortName(fieldDoc.type()), mapVisibility(fieldDoc), fieldDoc.isStatic());
            fields.add(mappedField);
        }
        return fields;
    }
    
    public List<Constructor> constructors() {
        List<Constructor> constructors = new ArrayList<>();
        for (ConstructorDoc consDoc: _classDoc.constructors(false)) {
            List<MethodParameter> params = new ArrayList<>();
            for (Parameter param: consDoc.parameters()) {
                params.add(new MethodParameter(shortName(param.type()), param.name()));
            }
            Constructor constructor = new Constructor(consDoc.name(), params, mapVisibility(consDoc));
            constructors.add(constructor);
        }
        return constructors;
    }
    
    public List<Method> methods() {
        List<Method> methods = new ArrayList<>();
        for (MethodDoc methodDoc: _classDoc.methods(false)) {
            List<MethodParameter> params = new ArrayList<>();
            for (Parameter param: methodDoc.parameters()) {
                params.add(new MethodParameter(shortName(param.type()), param.name()));
            }
            Method method = new Method(methodDoc.name(), 
                    params, 
                    shortName(methodDoc.returnType()), 
                    mapVisibility(methodDoc),
                    methodDoc.isAbstract(),
                    methodDoc.isStatic());
            methods.add(method);
        }
        return methods;
    }
    
    public static String fullName(Type type) {
        String params = buildParameterString(type);
        if (params.length() > 0) {
            return type.qualifiedTypeName() + "<" + params + ">";
        } else {
            return type.qualifiedTypeName();
        }
    }
    
    public static String shortName(Type type) {
        String params = buildParameterString(type);
        if (params.length() > 0) {
            return type.simpleTypeName() + "<" + params + ">";
        } else {
            return type.simpleTypeName();
        }
    }
    
    // Update Model
    
    public void addRelationship(ModelRel rel) {
        _rels.add(rel);
    }
    
    // Mapping
    
    private void mapRelationships() {
        mapSuperclass();
        mapInterfaces();
        mapFieldAssociations();
        mapParameterDependencies();
        mapMethodDependencies();
    }
    
    private void mapSuperclass() {
        Type superclassType = _classDoc.superclassType();
        if (superclassType != null) {
            String superclassName = superclassType.qualifiedTypeName();
            // Do not include standard Java superclasses in the model.
            if (!superclassName.equals("java.lang.Object") && !superclassName.equals("java.lang.Enum")) {
                ModelClass dest = _model.createClassIfNotExists(superclassType);
                ModelRel rel = new ModelRel(ModelRel.Kind.GENERALIZATION, this, dest);
                mapSourceRel(rel);
            }
        }
    }
    
    private void mapInterfaces() {
        for (Type interfaceType: _classDoc.interfaceTypes()) {
            ModelClass dest = _model.createClassIfNotExists(interfaceType);
            // If source class is an interface, than the relationship is a generalization, not a realization.
            ModelRel.Kind kind = _classDoc.isInterface() ? ModelRel.Kind.GENERALIZATION : ModelRel.Kind.REALIZATION;
            ModelRel rel = new ModelRel(kind, this, dest);
            mapSourceRel(rel);
        }        
    }
    
    private void mapFieldAssociations() {
        for (FieldDoc fieldDoc: _classDoc.fields()) {
            Type type = fieldDoc.type();
            String typeName = type.qualifiedTypeName();
            // TODO Relationships through collection types.
            if (!type.simpleTypeName().equals("void") && !typeName.startsWith("java.lang.") && !type.isPrimitive()) {
                ModelClass dest = _model.createClassIfNotExists(type);
                ModelRel rel = new ModelRel(ModelRel.Kind.DIRECTED_ASSOCIATION, this, dest, fieldDoc.name());
                mapSourceRel(rel);
            }
        }
    }
    
    private void mapMethodDependencies() {
        for (MethodDoc methodDoc: _classDoc.methods()) {
            if (methodDoc.isPublic()) {
                for (Parameter param: methodDoc.parameters()) {
                    Type type = param.type();
                    mapTypeDependency(type);
                }
                Type returnType = methodDoc.returnType();
                mapTypeDependency(returnType);
            }
        }
    }
    
    private void mapParameterDependencies() {
        ParameterizedType paramType = _type.asParameterizedType();
        if (paramType != null) {
            for (Type param : paramType.typeArguments()) {
                mapTypeDependency(param);
            }
        }
    }

    private void mapTypeDependency(Type type) {
        String typeName = type.qualifiedTypeName();
        // TODO Relationships through collection types.
        if (!type.simpleTypeName().equals("void") && !typeName.startsWith("java.lang.") && !type.isPrimitive()) {
            ModelClass dest = _model.createClassIfNotExists(type);
            // Only add if there is no existing relationship with the class.
            // Do not add dependency to this class.
            if (this != dest && !hasRelationshipWith(dest)) {
                ModelRel rel = new ModelRel(ModelRel.Kind.DEPENDENCY, this, dest);
                mapSourceRel(rel);
            }
        }
    }
    
    private void mapSourceRel(ModelRel rel) {
        _rels.add(rel);
        // Do not add relationships back to ourselves more than once.
        ModelClass dest = rel.destination();
        if (this != dest) {
            dest.addRelationship(rel);
        }
    }
    
    private Visibility mapVisibility(ProgramElementDoc doc) {
        if (doc.isPublic()) {
            return Visibility.PUBLIC;
        } else if(doc.isProtected()) {
            return Visibility.PROTECTED;
        } else if(doc.isPrivate()) {
            return Visibility.PRIVATE;
        } else {
            return Visibility.PACKAGE_PRIVATE;
        }
    }

    private static String buildParameterString(Type type) {
        StringBuilder sb = new StringBuilder();
        ParameterizedType paramType = type.asParameterizedType();
        if (paramType != null) {
            String sep = "";
            for (Type param : paramType.typeArguments()) {
                sb.append(sep);
                sb.append(param.simpleTypeName());
                sep = ", ";
            }
        }
        return sb.toString();
    }
    
    private final Model _model;
    private final Type _type;
    private final ClassDoc _classDoc;
    private final List<ModelRel> _rels = new ArrayList<>();
}
