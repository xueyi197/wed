package cn.lcy.ontologyconstruction.service;

import cn.lcy.ontologyconstruction.config.Config;
import cn.lcy.ontologyconstruction.dao.ConstructionDAOI;
import cn.lcy.ontologyconstruction.dao.ConstructionDAOImpl;
import cn.lcy.ontologyconstruction.enums.OntologyClassEnum;
import cn.lcy.ontologyconstruction.model.BaikePage;
import cn.lcy.ontologyconstruction.util.FileIOUtil;
import cn.lcy.ontologyconstruction.util.PictureDownloader;
import com.hankcs.hanlp.corpus.io.IOUtil;
import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntClass;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class AcademyConstructionServiceImpl implements ConstructionServiceI {

    private ConstructionDAOI constructionDAO = new ConstructionDAOImpl();

    @Override
    public boolean construction(BaikePage baikePage) throws Exception {
        /**实体名*/
        String individualName = baikePage.getLemmaTitle();
        String polysemantExplain = baikePage.getPolysemantExplain();
        String url = baikePage.getUrl();
        Individual academyIndividual = null;

        /**查询词典中是否有该实体，有则查询返回，没有则创建返回，true表示这是本名*/
        academyIndividual = this.queryIndividual(individualName, polysemantExplain, url, true, OntologyClassEnum.ACADEMY);
        constructionDAO.addObjectProperty(academyIndividual, "是", academyIndividual);

        /**添加数据属性*/
        String lemmaSummary = baikePage.getLemmaSummary();
        String picSrc = baikePage.getPicSrc();
        if (picSrc != null) {
            /**取得当前时间*/
            long times = System.currentTimeMillis();
            /**生成0-1000的随机数*/
            int random = (int) (Math.random() * 1000);
            /**扩展名称*/
            String newPicName = times + "" + random + ".jpg";
            PictureDownloader.picDownload(picSrc, newPicName, Config.picSavePath + File.separator + OntologyClassEnum.ACADEMY.getName());
            constructionDAO.addDataProperty(academyIndividual, "picSrc", newPicName);
        }
        constructionDAO.addDataProperty(academyIndividual, "URL信息来源", url);
        constructionDAO.addDataProperty(academyIndividual, "描述", lemmaSummary);
        constructionDAO.addDataProperty(academyIndividual, "歧义说明", polysemantExplain);

        /**添加基本信息*/
        List<String> parameterNamesFilter = baikePage.getParameterNames();
        List<String> parameterValuesFilter = baikePage.getParameterValues();
        constructionDAO.addDataProperties(academyIndividual, parameterNamesFilter, parameterValuesFilter);

        return false;
    }

    public Individual queryIndividual(String individualName, String polysemantExplain, String url, boolean isAliases, OntologyClassEnum parentClass) {
        /**读取Answer_Dict词典*/
        LinkedList<String> dictIndividualList = IOUtil.readLineListWithLessMemory(Config.individualDictPath);
        Individual academyIndividual = null;
        Long rowNum = 0l;经存在
        /**遍历词典中的实体记录 判断当前实体是否已*/
        for (String row : dictIndividualList) {
            ++rowNum;
            String[] fieldsDict = row.split("_");
            String dictIndividualUUID = fieldsDict[0]; // UUID
            String dictIndividualName = fieldsDict[1]; // 实体名
            String dictPolysemantExplain = fieldsDict[2]; // 歧义说明
            String dictIndividualURL = fieldsDict[3]; // 实体百科页面URL
            String dictIsAliasesWrite = fieldsDict[4]; // 是否是本名
            int dictIndividualClass = Integer.parseInt(fieldsDict[5]);    // 实体所属类型
            /**如果词典中歧义理解字段为待更新*/
            /**如果找到实体名相同且明确指出该实体没有歧义则，该实体就是当前迭代到的实体*/
            if (individualName.equals(dictIndividualName) && dictPolysemantExplain.equals("无")) {
                academyIndividual = constructionDAO.getIndividual(dictIndividualUUID);
                /**找到完全相同的实体了，使用#去除所有框架定位网页*/
            } else if (individualName.equals(dictIndividualName) && url.split("#")[0].equals(dictIndividualURL) && dictIndividualClass == parentClass.getIndex()) {
                /**如果此时抓到的实体歧义不为空，则表示该实体有同名实体，则更新词典*/
                if (dictPolysemantExplain.equals("待更新")) {
                    if (polysemantExplain == null) {
                        polysemantExplain = "无";
                    }
                    /**更新词典 修改歧义说明字段*/
                    row = dictIndividualUUID + "_" + dictIndividualName + "_" + polysemantExplain + "_" + dictIndividualURL + "_" + dictIsAliasesWrite + "_" + parentClass.getIndex();
                    /**更新Answer_Dict*/
                    FileIOUtil.updateContent(Config.individualDictPath, rowNum, row);
                }
                /**获取该实体*/
                academyIndividual = constructionDAO.getIndividual(dictIndividualUUID);
            }
        }

        /**如果词典中不存在该实体，则插入词典并且创建一个实体*/
        if (academyIndividual == null) {
            String movieIndividualUUID = UUID.randomUUID().toString().replace("-", "");
            String isAliasesWrite = null;
            if (isAliases == true) {
                isAliasesWrite = "1";
            } else {
                isAliasesWrite = "0";
            }
            if (polysemantExplain == null) {
                polysemantExplain = "无";
            }
            String row_add_individual = movieIndividualUUID + "_" + individualName + "_" + polysemantExplain + "_" + url.split("#")[0] + "_" + isAliasesWrite + "_" + parentClass.getIndex();
            FileIOUtil.appendContent(Config.individualDictPath, row_add_individual);
            OntClass movieClass = constructionDAO.getOntClass(parentClass.getName());        //获取电影类型
            academyIndividual = constructionDAO.createIndividual(movieIndividualUUID, movieClass);
        }
        constructionDAO.addComment(academyIndividual, individualName); //创建comment
        return academyIndividual;
    }

}
