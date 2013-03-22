package jadx.codegen;

import jadx.Consts;
import jadx.dex.attributes.AttributeFlag;
import jadx.dex.attributes.AttributeType;
import jadx.dex.attributes.AttributesList;
import jadx.dex.attributes.JadxErrorAttr;
import jadx.dex.attributes.annotations.MethodParameters;
import jadx.dex.info.AccessInfo;
import jadx.dex.instructions.args.ArgType;
import jadx.dex.instructions.args.RegisterArg;
import jadx.dex.nodes.InsnNode;
import jadx.dex.nodes.MethodNode;
import jadx.dex.trycatch.CatchAttr;
import jadx.dex.visitors.DepthTraverser;
import jadx.dex.visitors.FallbackModeVisitor;
import jadx.utils.ErrorsCounter;
import jadx.utils.InsnUtils;
import jadx.utils.Utils;
import jadx.utils.exceptions.CodegenException;
import jadx.utils.exceptions.DecodeException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.android.dx.rop.code.AccessFlags;

public class MethodGen {
	private static final Logger LOG = LoggerFactory.getLogger(MethodGen.class);

	private final MethodNode mth;
	private final Set<String> mthArgsDecls;
	private final Map<String, ArgType> varDecls = new HashMap<String, ArgType>();
	private final ClassGen classGen;
	private final boolean fallback;
	private final AnnotationGen annotationGen;

	public MethodGen(ClassGen classGen, MethodNode mth) {
		this.mth = mth;
		this.classGen = classGen;
		this.fallback = classGen.isFallbackMode();
		this.annotationGen = classGen.getAnnotationGen();

		List<RegisterArg> args = mth.getArguments(true);
		mthArgsDecls = new HashSet<String>(args.size());
		for (RegisterArg arg : args) {
			mthArgsDecls.add(makeArgName(arg));
		}
	}

	public ClassGen getClassGen() {
		return classGen;
	}

	public void addDefinition(CodeWriter code) {
		if (mth.getMethodInfo().isClassInit()) {
			code.startLine("static");
		} else {
			if (mth.getAttributes().contains(AttributeFlag.INCONSISTENT_CODE)) {
				code.startLine("// FIXME: Jadx generate inconsistent code");
				// ErrorsCounter.methodError(mth, "Inconsistent code");
			}

			annotationGen.addForMethod(code, mth);

			AccessInfo ai = mth.getAccessFlags();
			// don't add 'abstract' to methods in interface
			if (mth.getParentClass().getAccessFlags().isInterface()) {
				ai = ai.remove(AccessFlags.ACC_ABSTRACT);
			}

			code.startLine(ai.makeString());
			if (mth.getAccessFlags().isConstructor()) {
				code.add(classGen.getClassNode().getShortName()); // constructor
			} else {
				code.add(TypeGen.translate(classGen, mth.getMethodInfo().getReturnType()));
				code.add(" ");
				code.add(mth.getName());
			}
			code.add("(");

			mth.resetArgsTypes();
			List<RegisterArg> args = mth.getArguments(false);
			if (mth.getMethodInfo().isConstructor()
					&& mth.getParentClass().getAttributes().contains(AttributeType.ENUM_CLASS)) {
				if (args.size() == 2)
					args.clear();
				else if (args.size() > 2)
					args = args.subList(2, args.size());
				else
					LOG.warn(ErrorsCounter.formatErrorMsg(mth,
							"Incorrect number of args for enum constructor: " + args.size()
									+ " (expected >= 2)"));
			}
			code.add(makeArguments(args));
			code.add(")");

			annotationGen.addThrows(mth, code);
		}
	}

	public CodeWriter makeArguments(List<RegisterArg> args) {
		CodeWriter argsCode = new CodeWriter();

		MethodParameters paramsAnnotation =
				(MethodParameters) mth.getAttributes().get(AttributeType.ANNOTATION_MTH_PARAMETERS);

		int i = 0;
		for (Iterator<RegisterArg> it = args.iterator(); it.hasNext();) {
			RegisterArg arg = it.next();

			// add argument annotation
			if (paramsAnnotation != null)
				annotationGen.addForParameter(argsCode, paramsAnnotation, i);

			if (!it.hasNext() && mth.getAccessFlags().isVarArgs()) {
				// change last array argument to varargs
				ArgType type = arg.getType();
				if (type.isArray()) {
					ArgType elType = type.getArrayElement();
					argsCode.add(TypeGen.translate(classGen, elType));
					argsCode.add(" ...");
				} else {
					LOG.warn(ErrorsCounter.formatErrorMsg(mth, "Last argument in varargs method not array"));
					argsCode.add(TypeGen.translate(classGen, arg.getType()));
				}
			} else {
				argsCode.add(TypeGen.translate(classGen, arg.getType()));
			}
			argsCode.add(" ");
			argsCode.add(makeArgName(arg));

			i++;
			if (it.hasNext())
				argsCode.add(", ");
		}
		return argsCode;
	}

	/**
	 * Make variable name for register,
	 * Name contains register number and
	 * variable type or name (if debug info available)
	 */
	public String makeArgName(RegisterArg arg) {
		String name = arg.getTypedVar().getName();
		String base = "r" + arg.getRegNum();
		if (fallback) {
			if (name != null)
				return base + "_" + name;
			else
				return base;
		} else {
			if (name != null) {
				if (name.equals("this"))
					return name;
				else if (Consts.DEBUG)
					return name + "_" + base;
				else
					return name;
			} else {
				return base + "_" + Utils.escape(TypeGen.translate(classGen, arg.getType()));
			}
		}
	}

	/**
	 * Put variable declaration and return variable name (used for assignments)
	 * 
	 * @param arg
	 *            register variable
	 * @return variable name
	 */
	public String assignArg(RegisterArg arg) {
		String name = makeArgName(arg);
		if (!mthArgsDecls.contains(name))
			varDecls.put(name, arg.getType());
		return name;
	}

	private void makeInitCode(CodeWriter code) throws CodegenException {
		InsnGen igen = new InsnGen(this, mth, fallback);
		// generate super call
		if (mth.getSuperCall() != null)
			igen.makeInsn(mth.getSuperCall(), code);
	}

	public void makeVariablesDeclaration(CodeWriter code) {
		for (Entry<String, ArgType> var : varDecls.entrySet()) {
			code.startLine(TypeGen.translate(classGen, var.getValue()));
			code.add(" ");
			code.add(var.getKey());
			code.add(";");
		}
		if (!varDecls.isEmpty())
			code.endl();
	}

	public CodeWriter makeInstructions(int mthIndent) throws CodegenException {
		CodeWriter code = new CodeWriter(mthIndent + 1);

		if (mth.getAttributes().contains(AttributeType.JADX_ERROR)) {
			code.startLine("throw new UnsupportedOperationException(\"Method not decompiled: ");
			code.add(mth.toString());
			code.add("\");");

			JadxErrorAttr err = (JadxErrorAttr) mth.getAttributes().get(AttributeType.JADX_ERROR);
			code.startLine("// FIXME: Jadx error processing method");
			Throwable cause = err.getCause();
			if (cause != null) {
				code.endl().add("/*");
				code.startLine("Message: ").add(cause.getMessage());
				code.startLine("Error: ").add(Utils.getStackTrace(cause));
				code.add("*/");
			}

			// load original instructions
			try {
				mth.load();
				DepthTraverser.visit(new FallbackModeVisitor(), mth);
			} catch (DecodeException e) {
				// ignore
				return code;
			}

			code.startLine("/*");
			makeFullMethodDump(code, mth);
			code.startLine("*/");
		} else {
			if (mth.getRegion() != null) {
				CodeWriter insns = new CodeWriter(mthIndent + 1);
				(new RegionGen(this, mth)).makeRegion(insns, mth.getRegion());

				makeInitCode(code);
				makeVariablesDeclaration(code);
				code.add(insns);
			} else {
				makeFallbackMethod(code, mth);
			}
		}
		return code;
	}

	private void makeFullMethodDump(CodeWriter code, MethodNode mth) {
		getFallbackMethodGen(mth).addDefinition(code);
		code.add(" {");
		code.incIndent();

		makeFallbackMethod(code, mth);

		code.decIndent();
		code.startLine("}");
	}

	private void makeFallbackMethod(CodeWriter code, MethodNode mth) {
		if (!mth.getAccessFlags().isStatic()) {
			code.startLine(getFallbackMethodGen(mth).makeArgName(mth.getThisArg())).add(" = this;");
		}
		makeFallbackInsns(code, mth, mth.getInstructions(), true);
	}

	public static void makeFallbackInsns(CodeWriter code, MethodNode mth, List<InsnNode> insns, boolean addLabels) {
		InsnGen insnGen = new InsnGen(getFallbackMethodGen(mth), mth, true);
		for (InsnNode insn : insns) {
			AttributesList attrs = insn.getAttributes();
			if (addLabels) {
				if (attrs.contains(AttributeType.JUMP)
						|| attrs.contains(AttributeType.EXC_HANDLER)) {
					code.decIndent();
					code.startLine(getLabelName(insn.getOffset()) + ":");
					code.incIndent();
				}
			}
			try {
				insnGen.makeInsn(insn, code);
			} catch (CodegenException e) {
				code.startLine("// error: " + insn);
			}
			CatchAttr _catch = (CatchAttr) attrs.get(AttributeType.CATCH_BLOCK);
			if (_catch != null)
				code.add("\t // " + _catch);
		}
	}

	/**
	 * Return fallback variant of method codegen
	 */
	private static MethodGen getFallbackMethodGen(MethodNode mth) {
		ClassGen clsGen = new ClassGen(mth.getParentClass(), null, true);
		return new MethodGen(clsGen, mth);
	}

	public static String getLabelName(int offset) {
		return "L_" + InsnUtils.formatOffset(offset);
	}

}
