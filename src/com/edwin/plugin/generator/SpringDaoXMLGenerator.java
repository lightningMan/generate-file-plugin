package com.edwin.plugin.generator;

import com.edwin.plugin.Configuration;
import com.edwin.plugin.Constant;
import com.edwin.plugin.exception.BreakException;
import com.edwin.plugin.exception.GenerateException;
import com.google.common.base.Strings;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import java.util.Properties;

/**
 * Created By User: edwin. Time: 15-4-29 10:09.
 */
public class SpringDaoXMLGenerator extends AbstractXMLGenerator {

    /** IBatisGenericDaoImpl bean id */
    private String realizeTarget;

    private String dao;

    private String ibatisXml;

    private String daoXml;

    public SpringDaoXMLGenerator(Configuration configuration, Project project) {
        super(configuration, project);
    }

    @Override
    public void generateXML() throws Exception {

        if (Strings.isNullOrEmpty(configuration.getSpringIbatisRelativePath())) {
            throw new BreakException("没有配置spring ibatis文件路径，无法生成spring dao");
        }

        // spring ibatis
        String[] paths = configuration.getSpringIbatisRelativePath().split("/");
        VirtualFile virtualFile = resourceRoot;
        for (int i = 0; i < paths.length; i++) {
            if (i == paths.length - 1) {
                ibatisXml = paths[i];
                break;
            }

            VirtualFile virtualFileCopy = virtualFile.findChild(paths[i]);
            if (virtualFileCopy != null) {
                virtualFile = virtualFileCopy;
            } else {
                virtualFile = virtualFile.createChildDirectory(null, paths[i]);
            }
        }
        PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFile);
        VirtualFile springIbatisVirtualFile = resourceRoot.findFileByRelativePath(configuration.getSpringIbatisRelativePath());
        modifySpringIbatisXML(springIbatisVirtualFile, psiDirectory);

        // spring dao
        paths = configuration.getSpringDaoRelativePath().split("/");
        virtualFile = resourceRoot;
        for (int i = 0; i < paths.length; i++) {
            if (i == paths.length - 1) {
                daoXml = paths[i];
                break;
            }
            VirtualFile virtualFileCopy = virtualFile.findChild(paths[i]);
            if (virtualFileCopy != null) {
                virtualFile = virtualFileCopy;
            } else {
                virtualFile = virtualFile.createChildDirectory(null, paths[i]);
            }
        }
        psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFile);
        VirtualFile springDaoVirtualFile = resourceRoot.findFileByRelativePath(configuration.getSpringDaoRelativePath());

        modifySpringDaoXML(springDaoVirtualFile, psiDirectory);
    }

    private void modifySpringDaoXML(VirtualFile springDaoVirtualFile, PsiDirectory psiDirectory) throws Exception {

        try {

            dao = configuration.getPackageName() + "." + Constant.DEFAUL_DAO_PACKAGE + "."
                  + configuration.getClassName() + "Dao";

            if (springDaoVirtualFile == null) {
                FileTemplateUtil.createFromTemplate(springDaoTemplate, daoXml, buildDaoProperties(), psiDirectory);
                return;
            }

            if (springDaoVirtualFile.isDirectory()) {
                throw new BreakException(configuration.getSpringIbatisRelativePath() + " is directory. ");
            }

            if (Strings.isNullOrEmpty(realizeTarget)) {
                throw new BreakException(Constant.IBATIS_DAO_BEAN + "没有配置ID");
            }

            XmlFile xmlFile = (XmlFile) PsiManager.getInstance(project).findFile(springDaoVirtualFile);

            XmlTag rootTag = xmlFile.getRootTag();
            XmlTag[] beanTags = rootTag.findSubTags(Constant.BEAN);

            // 验证是否存在相同的dao
            validateSameDao(beanTags);

            String tagText = Constant.DAO_BEAN.replaceAll("_daoName_",
                                                          StringUtils.uncapitalize(configuration.getClassName())
                                                                  + "Dao").replaceAll("_dao_", dao).replaceAll("_realizeTarget_",
                                                                                                               realizeTarget).replaceAll("_namespace_",
                                                                                                                                         configuration.getClassName());

            XmlElementFactory xmlElementFactory = XmlElementFactory.getInstance(project);

            XmlTag sqlMapTag = xmlElementFactory.createTagFromText(tagText);

            rootTag.addSubTag(sqlMapTag, true);

            CodeStyleManager.getInstance(project).reformat(xmlFile);
        } catch (GenerateException e1) {
            throw new GenerateException(e1.getMessage());
        } catch (Exception e) {
            throw new BreakException(configuration.getSpringDaoRelativePath() + "文件格式错误，" + e.getMessage());
        }
    }

    private Properties buildDaoProperties() {

        Properties properties = new Properties();

        properties.put("DAOCLASSPATH", dao);
        properties.put("REALIZETARGET", realizeTarget);
        properties.put("NAMESPACE", configuration.getClassName());
        properties.put("DAONAME", StringUtils.uncapitalize(configuration.getClassName()) + "Dao");

        return properties;
    }

    private void validateSameDao(XmlTag[] beanTags) throws Exception {

        if (!ArrayUtils.isEmpty(beanTags)) {
            for (XmlTag sqlTag : beanTags) {
                XmlTag[] propertyTags = sqlTag.findSubTags(Constant.PROPERTY);
                if (ArrayUtils.isEmpty(propertyTags)) {
                    throw new BreakException("spring dao配置错误");
                }

                for (XmlTag propertyTag : propertyTags) {
                    if (propertyTag.getAttributeValue(Constant.NAME_ATTTIBUTE).equalsIgnoreCase(Constant.PROXY_INTERFACES)) {
                        if (propertyTag.getAttributeValue(Constant.VALUE_ATTTIBUTE).equalsIgnoreCase((dao))) {
                            throw new GenerateException("存在相同的spring dao");
                        }
                    }
                }
            }
        }
    }

    private void modifySpringIbatisXML(VirtualFile springIbatisVirtualFile, PsiDirectory psiDirectory) throws Exception {

        try {

            PsiElement virtualPsi = null;

            if (springIbatisVirtualFile == null) {
                virtualPsi = FileTemplateUtil.createFromTemplate(springIbatisTemplate, ibatisXml,
                                                                 buildIbatisProperties(), psiDirectory);
                springIbatisVirtualFile = virtualPsi.getContainingFile().getVirtualFile();
            }

            if (springIbatisVirtualFile.isDirectory()) {
                throw new BreakException(configuration.getSpringIbatisRelativePath() + " is directory. ");
            }

            if (virtualPsi == null) {
                virtualPsi = PsiManager.getInstance(project).findFile(springIbatisVirtualFile);
            }

            XmlFile xmlFile = (XmlFile) virtualPsi;

            XmlTag rootTag = xmlFile.getRootTag();
            XmlTag[] beanTags = rootTag.findSubTags(Constant.BEAN);
            if (ArrayUtils.isEmpty(beanTags)) {
                throw new BreakException("文件中没有ibatis配置");
            }

            XmlTag limitBeanTag = null;
            for (XmlTag sqlTag : beanTags) {
                String classAttribute = sqlTag.getAttributeValue(Constant.CLASS_ATTTIBUTE);
                if (classAttribute.equalsIgnoreCase(Constant.LIMIT_SQL_FACTORY_BEAN)) {
                    limitBeanTag = sqlTag;
                }

                if (classAttribute.equalsIgnoreCase(Constant.IBATIS_DAO_BEAN)) {
                    realizeTarget = sqlTag.getAttributeValue(Constant.ID_ATTTIBUTE);
                }
            }

            if (limitBeanTag == null) {
                throw new BreakException("文件中没有ibatis配置");
            }

            // 添加configLocations属性，这个想想还是不做了
            XmlTag[] propertyTags = limitBeanTag.findSubTags(Constant.PROPERTY);
            if (ArrayUtils.isEmpty(propertyTags)) {
                throw new BreakException("bean中没有发现" + Constant.PROPERTY + "配置");
            }
        } catch (Exception e) {
            throw new BreakException(configuration.getSpringIbatisRelativePath() + "文件格式错误，" + e.getMessage());
        }
    }

    private Properties buildIbatisProperties() {

        Properties properties = new Properties();

        properties.put("sqlMapPath", configuration.getSqlMapRelativePath() + "/" + Constant.SQL_MAP_CONFIG_XML);

        return properties;
    }
}
