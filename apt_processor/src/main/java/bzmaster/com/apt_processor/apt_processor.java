package bzmaster.com.apt_processor;

import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;


import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.Elements;

import bzmaster.com.apt_annotation.BindView;
import bzmaster.com.apt_processor.utils.ElementUtils;
import bzmaster.com.apt_processor.utils.StringUtils;

@AutoService(Processor.class)
public class apt_processor extends AbstractProcessor {
    private Elements elementUtils;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnvironment) {
        super.init(processingEnvironment);
        elementUtils=processingEnvironment.getElementUtils();
    }

    /**
     * 法用于指定该 AbstractProcessor 的目标注解对象，
     * */
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> hashSet=new HashSet<>();
        hashSet.add(BindView.class.getCanonicalName());
        return hashSet;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    /**
     * 用于处理包含指定注解对象的代码元素
     *
     * 注意：
     * 当中，Element 用于代表程序的一个元素，这个元素可以是：包、类、接口、变量、方法等多种概念。
     * 这里以 Activity 对象作为 Key ，通过 map 来存储不同 Activity 下的所有注解对象
     * */
    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {
        //获取所有包含BindView注解的元素
        Set<? extends Element> elementSet  = roundEnvironment.getElementsAnnotatedWith(BindView.class);
        Map<TypeElement,Map<Integer,VariableElement>> typeElementHashMap=new HashMap<>();
        for (Element element:elementSet) {
            //因为 BindView 的作用对象是 FIELD，因此 element 可以直接转化为 VariableElement
            VariableElement variableElement = (VariableElement) element;
            //getEnclosingElement 方法返回封装此 Element 的最里层元素
            //如果 Element 直接封装在另一个元素的声明中，则返回该封装元素
            //此处表示的即 Activity 类对象
            TypeElement typeElement = (TypeElement) variableElement.getEnclosingElement();

            Map<Integer, VariableElement> variableElementMap  = typeElementHashMap.get(typeElement);

            if(variableElementMap==null){
                variableElementMap=new HashMap<>();
                typeElementHashMap.put(typeElement,variableElementMap);
            }

            //获取注解值，即ViewId
            BindView bindAnnotation = variableElement.getAnnotation(BindView.class);
            int viewId=bindAnnotation.value();
            //将每个包含了BindView注解的字段对象以及其注解值保存起来
            variableElementMap.put(viewId,variableElement);
        }

        for (TypeElement key:typeElementHashMap.keySet()) {
            Map<Integer, VariableElement> elementMap = typeElementHashMap.get(key);
            String packageName = ElementUtils.getPackageName(elementUtils, key);
            JavaFile javaFile= JavaFile.builder(packageName,generateCodeByPoet(key,elementMap)).build();
            try {
                javaFile.writeTo(processingEnv.getFiler());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }
    /**
     * 生成方法
     *
     * @param typeElement        注解对象上层元素对象，即 Activity 对象
     * @param variableElementMap Activity 包含的注解对象以及注解的目标对象
     * @return
     */
    private MethodSpec generateMethodByPoet(TypeElement typeElement, Map<Integer, VariableElement> variableElementMap){
        ClassName className = ClassName.bestGuess(typeElement.getQualifiedName().toString());
       //方法参数名称
        String parameter="_"+ StringUtils.toLowerCaseFirstChar(className.simpleName());
        //类似于   public static void bind(Activity activity){}
        MethodSpec.Builder methodBuilder=MethodSpec.methodBuilder("bind")
                                         .addModifiers(Modifier.PUBLIC,Modifier.STATIC)
                                         .returns(void.class)
                                         .addParameter(className,parameter);
        for (int viewId: variableElementMap.keySet()) {
            VariableElement element = variableElementMap.get(viewId);
            //被注解的字段
            String name = element.getSimpleName().toString();
            //被注解的字段的对象类型的全名称
            String type = element.asType().toString();
            String text="{0}.{1}=({2})({3}.findViewById({4}));";
            methodBuilder.addCode(MessageFormat.format(text,parameter,name,type,parameter,String.valueOf(viewId)));
        }
        return methodBuilder.build();
    }

    /**
     * 生成java类
     * @param typeElement 注解对象上层元素对象
     * @param variableElementMap Activity 包含的注解对象以及注解的目标对象
     *
     * */
    private TypeSpec generateCodeByPoet(TypeElement typeElement,Map<Integer,VariableElement> variableElementMap){
       return TypeSpec.classBuilder(ElementUtils.getEnclosingClassName(typeElement)+"ViewBinding")
                      .addModifiers(Modifier.PUBLIC)
                      .addMethod(generateMethodByPoet(typeElement,variableElementMap))
               .build();

    }

}
