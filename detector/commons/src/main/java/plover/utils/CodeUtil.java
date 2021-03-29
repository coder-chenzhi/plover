package plover.utils;

import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CodeUtil {

	static final Logger logger = LoggerFactory.getLogger(CodeUtil.class);

	
	public static boolean isLoggerPrintMethod(String miNode) {
		Pattern p = Pattern.compile(
				"^\\b(\\S)*log(\\S)*\\.(info|trace|debug|error|warn|fatal)\\(.*\\)",
				Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
		Matcher m = p.matcher(miNode);
		return m.matches();
	}

    /**
     *
     * @param miNode log level, "NoMatch" if no log level is detected
     * @return
     */
	public static String getLoggerLevel(String miNode) {
        Pattern p = Pattern.compile(
                "^\\b(\\S)*log(\\S)*\\.(info|trace|debug|error|warn|fatal)\\(.*\\)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher m = p.matcher(miNode);
        if (m.matches()) {
            return m.group(3);
        } else {
            return "NoMatch";
        }
	}
	
	/**
	 * parse the given source using the Eclipse IScanner to count the number of
	 * lines, if exception happens, return the total line of code
	 *
	 * @param a_source
	 * @return SLOC, number of lines in a_source, not counting comments and blank lines
	 *
	 * @throws InvalidInputException
	 */
	public static int calculateNumberOfLines(String a_source) {
		String srcToCount = a_source.trim();
		Set<Integer> lineSet = new HashSet<Integer>();
		IScanner scanner = ToolFactory.createScanner(false, false, true, true);
		scanner.setSource(srcToCount.toCharArray());
		try {
			while (true) {
				int token = scanner.getNextToken();
				if (token == ITerminalSymbols.TokenNameEOF) {
					break;
				}
				int startPos = scanner.getCurrentTokenStartPosition();
				int lineNum = scanner.getLineNumber(startPos);
				lineSet.add(lineNum);
			}
		} catch (InvalidInputException e) {
			logger.warn("Invalid source in AbstractLinesOfCode", e);
		}
		return lineSet.size();
	}

	public static int calculateMcCabe(ASTNode node) {
		class ComplexityVisitor extends ASTVisitor {
			
			public int complexity = 0;
			
			@Override
		    public boolean visit(final CatchClause arg0) {
		        complexity++;
		        return super.visit(arg0);
		    }

		    @Override
		    public boolean visit(final ConditionalExpression arg0) {
		    	complexity++;
		        return super.visit(arg0);
		    }

		    @Override
		    public boolean visit(final DoStatement arg0) {
		    	complexity++;
		        return super.visit(arg0);
		    }

		    @Override
		    public boolean visit(final EnhancedForStatement arg0) {
		    	complexity++;
		        return super.visit(arg0);
		    }

		    @Override
		    public boolean visit(final ForStatement arg0) {
		    	complexity++;
		        return super.visit(arg0);
		    }

		    @Override
		    public boolean visit(final IfStatement arg0) {
		    	complexity++;
		        return super.visit(arg0);
		    }

		    @Override
		    public boolean visit(final SwitchCase arg0) {
		    	complexity++;
		        return super.visit(arg0);
		    }

		    @Override
		    public boolean visit(final WhileStatement arg0) {
		    	complexity++;
		        return super.visit(arg0);
		    }
		}
		ComplexityVisitor visitor = new ComplexityVisitor();
		node.accept(visitor);
		return visitor.complexity;
	}
	
	public static String methodDeclarationToString(MethodDeclaration method) {
		StringBuilder s = new StringBuilder();
		try {
			Type returnType = method.getReturnType2();
			if (returnType != null) {
				s.append(returnType.toString() + " ");
			}
		} catch (UnsupportedOperationException e) {
			System.out.println("UnsupportedOperationException in JSL2");
		}
		s.append(method.getName().toString() + "(");
		List<SingleVariableDeclaration> params = method.parameters();
		for (int i = 0; i < params.size(); i++) {
			s.append(params.get(i).getType() + " " + params.get(i).getName());
			if (i != params.size() - 1) {
				s.append(", ");
			}
		}
		s.append(")");
		return s.toString();
	}
	
	public static String[] valueToStringArray(String value) {
		if (value == null) {
			return new String[]{};
		} else if (value.contains(";")) {
            return value.split(";");
        } else {
            return new String[] {value};
        }
    }


    public static void main(String[] args) {
        System.out.println(CodeUtil.getLoggerLevel("logger.info(\"help\")"));
    }
}
