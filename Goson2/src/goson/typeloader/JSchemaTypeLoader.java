package goson.typeloader;

import goson.model.JsonMap;
import goson.parser.JSchemaParser;
import goson.parser.JsonParseError;
import goson.parser.JsonParseException;
import goson.typeloader.rpc.JSchemaCustomizedRPCType;
import goson.typeloader.rpc.JSchemaRPCType;
import goson.util.JSchemaUtils;
import gw.fs.IDirectory;
import gw.fs.IFile;
import gw.lang.reflect.IType;
import gw.lang.reflect.RefreshKind;
import gw.lang.reflect.TypeLoaderBase;
import gw.lang.reflect.TypeSystem;
import gw.lang.reflect.module.IModule;
import gw.util.GosuExceptionUtil;
import gw.util.Pair;
import gw.util.concurrent.LockingLazyVar;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

import org.raml.model.ActionType;
import org.raml.model.Raml;
import org.raml.model.Resource;
import org.raml.parser.rule.ValidationResult;
import org.raml.parser.visitor.RamlDocumentBuilder;
import org.raml.parser.visitor.RamlValidationService;

public class JSchemaTypeLoader extends TypeLoaderBase {

  private Map<String, IJSchemaType> _rawTypes = new HashMap<String, IJSchemaType>();
  private Map<IFile, List<String>> _filesToTypes = new HashMap<IFile, List<String>>();

  private static final String JSC_RPC_EXT = "jsc-rpc";
  private static final String RAML_EXT = "raml";
  private static final String JSC_EXT = "jsc";
  private static final String JSON_EXT = "json";
  private boolean _initing;

  @Override
  public Set<? extends CharSequence> getAllNamespaces() {
	 System.out.println("getAllNamespaces " + Arrays.toString(Thread.currentThread().getStackTrace()) );
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void refreshedNamespace(String s, IDirectory iDirectory, RefreshKind refreshKind) {
	  System.out.println("refreshedNamespace " + Arrays.toString(Thread.currentThread().getStackTrace()) );
	  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public boolean hasNamespace(String s) {
	  System.out.println("hasNamespace " + Arrays.toString(Thread.currentThread().getStackTrace()) );
    return false;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public Set<String> computeTypeNames() {
	  System.out.println("computeTypeNames " + Arrays.toString(Thread.currentThread().getStackTrace()) );
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  public JSchemaTypeLoader(IModule env) {
	 super(env);
	 maybeInitTypes();
	 gw.config.CommonServices.getTypeSystem().pushTypeLoader(env,this);
	 for(String x : _rawTypes.keySet()){
		 System.out.println("Trying to get type " + x);
		 System.out.println(this.getType(x));
	 }
	 
  }

  @Override
  public IType getType(String fullyQualifiedName) {
    maybeInitTypes();
    if (fullyQualifiedName == null || _rawTypes.get(fullyQualifiedName) == null) {
      return null;
    }
    IType iType = _rawTypes.get(fullyQualifiedName);
    return TypeSystem.getOrCreateTypeReference(iType);
  }

  //TODO cgross - this should be lazy
  private void maybeInitTypes() {
	System.out.println("MaybeInitTypes " + _jscFiles.get().toArray());
    if (!_initing) {
      if (_rawTypes.isEmpty()) {
    	  System.out.println("Raw types was empty, starting init");
        _initing = true;
        try {
        	System.out.println("Trying to init jscFiles");
          for (JsonFile jshFile : _jscFiles.get()) {
            try {
              jshFile.parseContent();
              addRootType(_rawTypes, new Stack<Map<String, String>>(), jshFile, jshFile.file, _filesToTypes);
            } catch (Exception e) {
              throw GosuExceptionUtil.forceThrow(e);
            }
          }
          int counter = 0;
          for(JsonFile ramlFile : _ramlFiles.get()){
        	  System.out.println("Raml file: " + ramlFile.rootTypeName);
        	  String ramlLocation = ramlFile.file.toJavaFile().getAbsolutePath();
        	  Raml raml = new RamlDocumentBuilder().build(ramlLocation);
        	  
        	  for(String resKey : raml.getResources().keySet()){
        		  Resource res = raml.getResources().get(resKey);
        		  for(ActionType actKey : res.getActions().keySet()){
        			  org.raml.model.Action act = res.getActions().get(actKey);
        			  for(String bodKey : act.getBody().keySet())
        			  {
        				  org.raml.model.MimeType bod = act.getBody().get(bodKey);
        				  String sch = bod.getSchema();
        				  File schemaFile = new File(ramlFile.file.toJavaFile().getParent() + "/" + res.getDisplayName() + counter + "schema.json");
        				  
        			  }
        		  }
        	  }
        	  
        	  System.out.println(raml.getTitle());
          }
      	System.out.println("Trying to init rpc");
          for (JsonFile jshRpcFile : _jscRpcFiles.get()) {
            try {
              jshRpcFile.parseContent();
              addRpcTypes(_rawTypes, jshRpcFile, jshRpcFile.file, _filesToTypes);
            } catch (Exception e) {
              throw GosuExceptionUtil.forceThrow(e);
            }
          }
      	System.out.println("Trying to init jsonFiles");          
          for (JsonFile jsonFile : _jsonFiles.get()) {
            try {
              jsonFile.parseContent();
              convertToJSchemaAndAddRootType(_rawTypes, jsonFile, jsonFile.file, _filesToTypes);
            } catch (Exception e) {
              throw GosuExceptionUtil.forceThrow(e);
            }
          }
          initInnerClasses(_rawTypes);
        } finally {
          _initing = false;
        }
      }
    }
  }

  public List<IType> refreshedFile(IFile file) {
    List<String> typeNames = getTypeNamesForFile(file);
    if (file.getExtension().equals(JSC_EXT) ||
        file.getExtension().equals(JSC_RPC_EXT) ||
        file.getExtension().equals(JSON_EXT)) {
      _rawTypes.clear();
      _filesToTypes.clear();
      if (file.getExtension().equals(JSC_EXT)) {
        _jscFiles.clear();
      }
      if (file.getExtension().equals(JSC_RPC_EXT)) {
        _jscRpcFiles.clear();
      }
      if (file.getExtension().equals(JSON_EXT)) {
        _jsonFiles.clear();
      }
    }
    ArrayList<IType> types = new ArrayList<IType>();
    for (String typeName : typeNames) {
      IType type = TypeSystem.getByFullNameIfValid(typeName);
      if (type != null) {
        types.add(type);
      }
    }
    return types;
  }

  public String[] getTypesForFile(IFile file) {
    ArrayList<String> types = new ArrayList<String>();
    List<String> typeNamesForFile = getTypeNamesForFile(file);
    for (String s : typeNamesForFile) {
      IType type = TypeSystem.getByFullNameIfValid(s);
      if (type != null) {
        types.add(type.toString());
      }
    }
    return types.toArray(new String[0]);
  }

  private List<String> getTypeNamesForFile(IFile file) {
    maybeInitTypes(); //TODO cgross - this really, really needs to be lazy
    List<String> typeNames = _filesToTypes.get(file);
    if (typeNames == null) {
      typeNames = Collections.emptyList();
    }
    return typeNames;
  }

  private void convertToJSchemaAndAddRootType(Map<String, IJSchemaType> rawTypes, JsonFile jsonFile, IFile file, Map<IFile, List<String>> fileMapping) {
    jsonFile.content = JSchemaUtils.convertJsonToJSchema(jsonFile.content);
    addRootType(rawTypes, new Stack<Map<String, String>>(), jsonFile, file, fileMapping);
    return;
  }

  private void initInnerClasses(Map<String, IJSchemaType> rawTypes) {
    for (String name : rawTypes.keySet()) {
      IType iType = rawTypes.get(name);
      IType outerType = rawTypes.get(iType.getNamespace());
      if (outerType instanceof IJSchemaType) {
        ((IJSchemaType) outerType).addInnerClass(iType);
      }
    }
  }

  private void addRootType(Map<String, IJSchemaType> rawTypes, Stack<Map<String, String>> typeDefs, JsonFile jshFile, IFile file, Map<IFile, List<String>> fileMapping) {
    if (jshFile.content instanceof List) {
      System.out.println("jshFile instance of list");
      int depth = 0;
      while (jshFile.content instanceof List && ((List) jshFile.content).size() > 0) {
        System.out.println("Recursing down " + jshFile.content.toString());
    	depth++;
        jshFile.content = ((List) jshFile.content).get(0);
      }
      addTypes(rawTypes, typeDefs, jshFile.rootTypeName + ".Element", jshFile.content, file, fileMapping);
      JSchemaListWrapperType rawType = new JSchemaListWrapperType(jshFile.rootTypeName, this, depth, jshFile.content);
      rawTypes.put(jshFile.rootTypeName, rawType);
      rawType.addErrors(jshFile.errors);
    } else {
      addTypes(rawTypes, typeDefs, jshFile.rootTypeName, jshFile.content, file, fileMapping);
      IJSchemaType rootType = rawTypes.get(jshFile.rootTypeName);
      if (rootType instanceof JSchemaTypeBase) {
        ((JSchemaTypeBase) rootType).addErrors(jshFile.errors);
      }
    }
  }
  
  private void addTypes(Map<String, IJSchemaType> rawTypes, Stack<Map<String, String>> typeDefs, String name, Object o, IFile file, Map<IFile, List<String>> fileMapping) {
    // Handles this "customers" : [{ "name" : "string", "id" : "int"}]
    // i.e. an type def in an array field def
    while (o instanceof List && !((List)o).isEmpty()) {
      o = ((List)o).get(0);
    }
    if (o instanceof Map) {
      Map<Object, Object> jsonMap = (Map<Object, Object>)o;
      if (jsonMap.get(JSchemaUtils.JSCHEMA_ENUM_KEY) != null) {
        putType(rawTypes, name, new JSchemaEnumType(name, this, o), file, fileMapping);
      } else if (jsonMap.get("map_of") != null) {
        addTypes(rawTypes, typeDefs, name, jsonMap.get("map_of"), file, fileMapping);
      } else {
        try {
          typeDefs.push(new HashMap<String, String>());
          processTypeDefs(rawTypes, typeDefs, name, jsonMap, file, fileMapping);
          for (Object key : jsonMap.keySet()) {
            if (!JSchemaUtils.JSCHEMA_TYPEDEFS_KEY.equals(key)) {
              // RECURSION. This will call for every field in the definition. We rely on the if(o instanceof Map) thing up
              // there to cause those calls to be ignored.
              if (key != null) {
            	  System.out.println("Adding " + name + "." + JSchemaUtils.convertJSONStringToGosuIdentifier(key.toString()));
                addTypes(rawTypes, typeDefs, name + "." + JSchemaUtils.convertJSONStringToGosuIdentifier(key.toString()), jsonMap.get(key), file, fileMapping);
              }
            }
          }
          System.out.println("Putting new type " + name);
          putType(rawTypes, name, new JSchemaType(name, this, o, copyTypeDefs(typeDefs)), file, fileMapping);
          System.out.println(getType(name));
        } finally {
          typeDefs.pop();
        }
      }
    }
  }

  private void putType(Map<String, IJSchemaType> rawTypes, String name, IJSchemaType type, IFile file, Map<IFile, List<String>> fileMapping) {
    rawTypes.put(name, type);
    List<String> iTypes = fileMapping.get(file);
    if (iTypes == null) {
      iTypes = new ArrayList<String>();
      fileMapping.put(file, iTypes);
    }
    iTypes.add(type.getName());
  }

  private Map<String, String> copyTypeDefs(Stack<Map<String, String>> typeDefs) {
    HashMap<String, String> allTypeDefs = new HashMap<String, String>();
    for (Map<String, String> typeDef : typeDefs) {
      allTypeDefs.putAll(typeDef);
    }
    return allTypeDefs;
  }

  private void processTypeDefs(Map<String, IJSchemaType> types, Stack<Map<String, String>> typeDefs, String name, Map o, IFile file, Map<IFile, List<String>> fileMapping) {
    Object currentTypeDefs = o.get(JSchemaUtils.JSCHEMA_TYPEDEFS_KEY);
    if (currentTypeDefs instanceof Map) {
      Set set = ((Map) currentTypeDefs).keySet();
      List<IJSchemaType> previousTypeDefs = new ArrayList<IJSchemaType>();
      for (Object typeDefTypeName : set) {
        String rawName = typeDefTypeName.toString();
        String relativeName = JSchemaUtils.convertJSONStringToGosuIdentifier(rawName);
        String fullyQualifiedName = name + "." + relativeName;
        typeDefs.peek().put(rawName, fullyQualifiedName);
        addTypes(types, typeDefs, fullyQualifiedName, ((Map) currentTypeDefs).get(typeDefTypeName), file, fileMapping);
        for (IJSchemaType previousTypeDef : previousTypeDefs) {
          previousTypeDef.getTypeDefs().put(rawName, fullyQualifiedName);
        }
        previousTypeDefs.add(types.get(fullyQualifiedName));
      }
    }
  }

  private void addRpcTypes(Map<String, IJSchemaType> types, JsonFile jshRpcFile, IFile file, Map<IFile, List<String>> fileMapping)
  {
    Stack<Map<String, String>> typeDefs = new Stack<Map<String, String>>();
    typeDefs.push(new HashMap<String, String>());
    Map<String, Map<String, Object>> defaultValues = new HashMap<String, Map<String, Object>>();
    if (jshRpcFile.content instanceof Map) {
      processTypeDefs(types, typeDefs, jshRpcFile.rootTypeName, (Map) jshRpcFile.content, file, fileMapping);
      Object functions = ((Map) jshRpcFile.content).get(JSchemaUtils.JSCHEMA_FUNCTIONS_KEY);
      if (functions instanceof List) {
        for (Object function : (List) functions) {
          if (function instanceof Map) {
            Map functionMap = (Map) function;
            Object name = functionMap.get("name");
            if (name == null) {
              name = "badName";
            }
            String str = name.toString();
            String functionTypeName =  jshRpcFile.rootTypeName + "." + JSchemaUtils.convertJSONStringToGosuIdentifier(str);

            // add parameter names
            Object args = functionMap.get("args");
            if (args instanceof List) {
              for (Object arg : (List) args) {
                if (arg instanceof Map) {
                  Set argSpecKeys = ((Map) arg).keySet();
                  for (Object key : argSpecKeys) {
                    if (key.equals("default")) {
                      Map<String, Object> argsMap = defaultValues.get(str);
                      if (argsMap == null) {
                        argsMap = new HashMap<String, Object>();
                        defaultValues.put(str, argsMap);
                      }
                      argsMap.put(((Map) arg).keySet().iterator().next().toString(), ((Map) arg).get("default"));
                    } else if (key.equals("description")) {
                      //ignore
                    } else {
                      addTypes(types,
                        typeDefs, functionTypeName + "." + JSchemaUtils.convertJSONStringToGosuIdentifier(key.toString()),
                        ((Map) arg).get(key), file, fileMapping);
                    }
                  }
                }
              }
            }
            // add the return type
            Object returns = ((Map) function).get("returns");
            if (returns != null) {
              addTypes(types, typeDefs, functionTypeName, returns, file, fileMapping);
            }

            if (types.get(functionTypeName) == null) {
              // add in a dummy type to hold inner classes
              addTypes(types, typeDefs, functionTypeName, new HashMap(), file, fileMapping);
            }
          }
        }
      }
      JSchemaRPCType rpcType = new JSchemaRPCType(jshRpcFile.rootTypeName, this, jshRpcFile.content, typeDefs.peek(), defaultValues, jshRpcFile.stringContent);
      putType(types, rpcType.getName(), rpcType, file, fileMapping);
      rpcType.addErrors(jshRpcFile.errors);

      String customizedTypeName = jshRpcFile.rootTypeName + JSchemaCustomizedRPCType.TYPE_SUFFIX;
      JSchemaCustomizedRPCType rpcType2 = new JSchemaCustomizedRPCType(customizedTypeName, this, jshRpcFile.content, typeDefs.peek(), defaultValues, jshRpcFile.stringContent);
      putType(types, rpcType2.getName(), rpcType2, file, fileMapping);
    }
  }

 /* @Override
  public Set<String> getAllTypeNames() {
    maybeInitTypes();
    return new HashSet<String>( _rawTypes.keySet() );
  }*/

  @Override
  public List<String> getHandledPrefixes() {
	List<String> ret = new ArrayList<String>();
	ret.add("jsc");
    return Collections.emptyList();
  }

  /*
  * Default implementation to handle Gosu 0.9 reqs
  */
//  @Override TODO restore later
  public boolean handlesNonPrefixLoads() {
    return true;
  }

  private LockingLazyVar<List<JsonFile>> _jscFiles = new LockingLazyVar<List<JsonFile>>() {
    @Override
    protected List<JsonFile> init() {
      System.out.println("Lazy init");
      return findFilesOfType(JSC_EXT);
    }
  };

  private LockingLazyVar<List<JsonFile>> _jscRpcFiles = new LockingLazyVar<List<JsonFile>>() {
    @Override
    protected List<JsonFile> init() {
      return findFilesOfType(JSC_RPC_EXT);
    }
  };
  
  private LockingLazyVar<List<JsonFile>> _ramlFiles = new LockingLazyVar<List<JsonFile>>() {
	    @Override
	    protected List<JsonFile> init() {
	      return findFilesOfType(RAML_EXT);
	    }
	  };

  private LockingLazyVar<List<JsonFile>> _jsonFiles = new LockingLazyVar<List<JsonFile>>() {
    @Override
    protected List<JsonFile> init() {
      return findFilesOfType(JSON_EXT);
    }
  };

  private List<JsonFile> findFilesOfType(String extension) {
	System.out.println("Entering findFiles ofType " + extension);
    List<JsonFile> init = new java.util.ArrayList<JsonFile>();

    List<Pair<String, IFile>> files = getModule().getFileRepository().findAllFilesByExtension(extension);
    for (Pair<String, IFile> pair : files) {
      JsonFile current = new JsonFile();
      current.file = pair.getSecond();
      String relativeNameAsFile = pair.getFirst();
      int trimmedLength = relativeNameAsFile.length() - extension.length() - 1;
      String typeName = relativeNameAsFile.replace('/', '.').replace('\\', '.').substring(0, trimmedLength);
      if (typeName.indexOf('.') == -1) {
        //TODO ignore?
        throw new RuntimeException("Cannot have Simple JSON Schema definitions in the default package");
      }
      current.rootTypeName = typeName;
      System.out.println("Adding file: " + current.file.getName());	
      init.add(current);
    }
    return init;
  }

  private static class JsonFile {
    private Object content;
    private String stringContent;
    private String rootTypeName;
    private IFile file;
    private List<JsonParseError> errors;

    @Override
    public String toString() {
      return file.getPath().getPathString();
    }

    public void parseContent() {
      Scanner s = null;
      try {
        StringBuilder jsonString = new StringBuilder();
        s = new Scanner(file.toJavaFile());
        while (s.hasNextLine()) {
          jsonString.append(s.nextLine());
          jsonString.append("\n");
        }
        stringContent = jsonString.toString();
      } catch (FileNotFoundException e) {
        throw new RuntimeException(e);
      }
      JSchemaParser parser = new JSchemaParser(stringContent);
      try{
        content = parser.parseJSchema();
      } catch (JsonParseException e) {
        content = parser.getValue();
        if (content == null) {
          content = new JsonMap();
        }
        errors = parser.getErrors();
      } finally {
        if (s != null) { s.close(); }
      }
    }
  }
}
