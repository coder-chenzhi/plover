package plover.guards;

import com.alibaba.fastjson.annotation.JSONField;

import java.util.List;

public class Unit2JDKMethods {
    @JSONField(ordinal = 1)
    private String loggingID;
    @JSONField(ordinal = 2)
    private String methodName;
    @JSONField(ordinal = 3)
    private String lineNum;
    @JSONField(ordinal = 4)
    private String unitContent;
    @JSONField(ordinal = 5)
    private List<String> jdkMethods;

    public Unit2JDKMethods(String loggingID, String methodName, String unitContent, String lineNum, List<String> jdkMethods) {
        this.loggingID = loggingID;
        this.methodName = methodName;
        this.unitContent = unitContent;
        this.lineNum = lineNum;
        this.jdkMethods = jdkMethods;
    }

    public String getLoggingID() {
        return loggingID;
    }

    public void setLoggingID(String loggingID) {
        this.loggingID = loggingID;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getLineNum() {
        return lineNum;
    }

    public void setLineNum(String lineNum) {
        this.lineNum = lineNum;
    }

    public String getUnitContent() {
        return unitContent;
    }

    public void setUnitContent(String unitContent) {
        this.unitContent = unitContent;
    }

    public List<String> getJdkMethods() {
        return jdkMethods;
    }

    public void setJdkMethods(List<String> jdkMethods) {
        this.jdkMethods = jdkMethods;
    }

    @Override
    public String toString() {
        return "Unit2JDKMethods{" +
                "loggingID='" + loggingID + '\'' +
                ", methodName='" + methodName + '\'' +
                ", unitContent='" + unitContent + '\'' +
                ", lineNum='" + lineNum + '\'' +
                ", jdkMethods=" + jdkMethods +
                '}';
    }
}
