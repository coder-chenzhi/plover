package plover.jdt;

import plover.utils.CodeUtil;
import org.eclipse.jdt.core.dom.*;

import java.util.Arrays;
import java.util.regex.Pattern;

class Visitor extends ASTVisitor {

    private DataFrame df;
    private String filePath;
    private String fileContent;
    private CompilationUnit unit;
    private static Pattern guard = Pattern.compile("is(Trace|Debug|Info|Warn|Error|Fatal)Enabled");

    Visitor() throws Exception {
        df = DataFrame.newInstance(Arrays.asList("Path", "Line", "Level",
                "SLOC", "isInIfCondition", "PureCondition", "ConditionExpression", "Source"));
    }

    /**
     * if the logging guard is in condition expression of IfStatement, return the IfStatement,
     * otherwise, return null
     * @param node
     * @return
     */
    private IfStatement getIfStatement(MethodInvocation node) {
        ASTNode current = node;
        while (current.getParent() != null) {
            if (current.getParent() instanceof IfStatement) {
                if (((IfStatement) current.getParent()).getExpression().equals(current)) {
                    return ((IfStatement) current.getParent());
                }
            }
            current = current.getParent();
        }
        return null;
    }

    public boolean visit(MethodInvocation node) {
        if (guard.matcher(node.getName().toString()).matches()) {
            String level = node.getName().toString().replace("is", "").replace("Enabled", "");
            String line = String.valueOf(unit.getLineNumber(node.getStartPosition()));
            String sloc = "0";
            String isInIfCondition;
            String pureCondition;
            String conditionExpr;
            String source;
            IfStatement ifStmt = getIfStatement(node);
            if (ifStmt != null) {
                isInIfCondition = "true";
                conditionExpr = ifStmt.getExpression().toString();
                pureCondition = (node.getParent() instanceof IfStatement)? "true" : "false";
                source = ifStmt.toString().replace("\n", "").replace("\t", "");
                sloc = String.valueOf(CodeUtil.calculateNumberOfLines(
                        fileContent.substring(ifStmt.getStartPosition(), ifStmt.getStartPosition() + ifStmt.getLength())));

            } else {
                isInIfCondition = "false";
                conditionExpr = "NotInIfStmt";
                pureCondition = "NotInIfStmt";
                ASTNode sourceNode = node;
                while (sourceNode != null) {
                    if (sourceNode instanceof Statement || sourceNode instanceof VariableDeclaration) {
                        break;
                    }
                    sourceNode = sourceNode.getParent();
                }
                if (sourceNode != null) {
                    source = sourceNode.toString().replace("\n", "").replace("\t", "");
                } else {
                    source = "NoSource";
                }

                sloc = String.valueOf(CodeUtil.calculateNumberOfLines(
                        fileContent.substring(node.getStartPosition(), node.getStartPosition() + node.getLength())));

            }

            try {
                df.addRow(Arrays.asList(filePath, line, level, sloc,
                        isInIfCondition, pureCondition, conditionExpr, source));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    public void doVisit(String filePath, String fileContent) {
        this.filePath = filePath;
        this.fileContent = fileContent;
        ASTParser parser = ASTParser.newParser(AST.JLS12);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setStatementsRecovery(true);
        parser.setSource(fileContent.toCharArray());
        unit = (CompilationUnit) parser.createAST(null);
        unit.accept(this);
    }

    public String getResult() {
        return df.toString();
    }

}