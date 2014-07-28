package jadx.core.codegen;

import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.attributes.AType;
import jadx.core.dex.attributes.annotations.MethodParameters;
import jadx.core.dex.attributes.nodes.JadxErrorAttr;
import jadx.core.dex.info.AccessInfo;
import jadx.core.dex.instructions.args.ArgType;
import jadx.core.dex.instructions.args.RegisterArg;
import jadx.core.dex.nodes.InsnNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.trycatch.CatchAttr;
import jadx.core.dex.visitors.DepthTraversal;
import jadx.core.dex.visitors.FallbackModeVisitor;
import jadx.core.utils.ErrorsCounter;
import jadx.core.utils.InsnUtils;
import jadx.core.utils.Utils;
import jadx.core.utils.exceptions.CodegenException;
import jadx.core.utils.exceptions.DecodeException;

import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.android.dx.rop.code.AccessFlags;

public class MethodGen {
	private static final Logger LOG = LoggerFactory.getLogger(MethodGen.class);

	private final MethodNode mth;
	private final ClassGen classGen;
	private final AnnotationGen annotationGen;
	private final NameGen nameGen;

	public MethodGen(ClassGen classGen, MethodNode mth) {
		this.mth = mth;
		this.classGen = classGen;
		this.annotationGen = classGen.getAnnotationGen();
		this.nameGen = new NameGen(classGen.isFallbackMode());
	}

	public ClassGen getClassGen() {
		return classGen;
	}

	public NameGen getNameGen() {
		return nameGen;
	}

	public MethodNode getMethodNode() {
		return mth;
	}

	public boolean addDefinition(CodeWriter code) {
		if (mth.getMethodInfo().isClassInit()) {
			code.startLine("static");
			code.attachDefinition(mth);
			return true;
		}
		if (mth.contains(AFlag.ANONYMOUS_CONSTRUCTOR)) {
			// don't add method name and arguments
			code.startLine();
			code.attachDefinition(mth);
			return false;
		}
		annotationGen.addForMethod(code, mth);

		AccessInfo clsAccFlags = mth.getParentClass().getAccessFlags();
		AccessInfo ai = mth.getAccessFlags();
		// don't add 'abstract' and 'public' to methods in interface
		if (clsAccFlags.isInterface()) {
			ai = ai.remove(AccessFlags.ACC_ABSTRACT);
			ai = ai.remove(AccessFlags.ACC_PUBLIC);
		}
		// don't add 'public' for annotations
		if (clsAccFlags.isAnnotation()) {
			ai = ai.remove(AccessFlags.ACC_PUBLIC);
		}
		code.startLine(ai.makeString());
		code.attachSourceLine(mth.getSourceLine());

		if (classGen.addGenericMap(code, mth.getGenericMap())) {
			code.add(' ');
		}
		if (mth.getAccessFlags().isConstructor()) {
			code.add(classGen.getClassNode().getShortName()); // constructor
		} else {
			classGen.useType(code, mth.getReturnType());
			code.add(' ');
			code.add(mth.getName());
		}
		code.add('(');

		List<RegisterArg> args = mth.getArguments(false);
		if (mth.getMethodInfo().isConstructor()
				&& mth.getParentClass().contains(AType.ENUM_CLASS)) {
			if (args.size() == 2) {
				args.clear();
			} else if (args.size() > 2) {
				args = args.subList(2, args.size());
			} else {
				LOG.warn(ErrorsCounter.formatErrorMsg(mth,
						"Incorrect number of args for enum constructor: " + args.size()
								+ " (expected >= 2)"
				));
			}
		}
		addMethodArguments(code, args);
		code.add(')');

		annotationGen.addThrows(mth, code);
		code.attachDefinition(mth);
		return true;
	}

	private void addMethodArguments(CodeWriter argsCode, List<RegisterArg> args) {
		MethodParameters paramsAnnotation = mth.get(AType.ANNOTATION_MTH_PARAMETERS);
		int i = 0;
		for (Iterator<RegisterArg> it = args.iterator(); it.hasNext(); ) {
			RegisterArg arg = it.next();

			// add argument annotation
			if (paramsAnnotation != null) {
				annotationGen.addForParameter(argsCode, paramsAnnotation, i);
			}
			if (!it.hasNext() && mth.getAccessFlags().isVarArgs()) {
				// change last array argument to varargs
				ArgType type = arg.getType();
				if (type.isArray()) {
					ArgType elType = type.getArrayElement();
					classGen.useType(argsCode, elType);
					argsCode.add("...");
				} else {
					LOG.warn(ErrorsCounter.formatErrorMsg(mth, "Last argument in varargs method not array"));
					classGen.useType(argsCode, arg.getType());
				}
			} else {
				classGen.useType(argsCode, arg.getType());
			}
			argsCode.add(' ');
			argsCode.add(nameGen.assignArg(arg));

			i++;
			if (it.hasNext()) {
				argsCode.add(", ");
			}
		}
	}

	public void addInstructions(CodeWriter code) throws CodegenException {
		if (mth.contains(AType.JADX_ERROR)
				|| mth.contains(AFlag.INCONSISTENT_CODE)
				|| mth.getRegion() == null) {
			code.startLine("throw new UnsupportedOperationException(\"Method not decompiled: ")
					.add(mth.toString())
					.add("\");");

			if (mth.contains(AType.JADX_ERROR)) {
				JadxErrorAttr err = mth.get(AType.JADX_ERROR);
				code.startLine("/* JADX: method processing error */");
				Throwable cause = err.getCause();
				if (cause != null) {
					code.newLine();
					code.add("/*");
					code.startLine("Error: ").add(Utils.getStackTrace(cause));
					code.add("*/");
				}
			}
			code.startLine("/*");
			addFallbackMethodCode(code);
			code.startLine("*/");
		} else {
			RegionGen regionGen = new RegionGen(this);
			regionGen.makeRegion(code, mth.getRegion());
		}
	}

	public void addFallbackMethodCode(CodeWriter code) {
		if (mth.getInstructions() == null) {
			// load original instructions
			try {
				mth.load();
				DepthTraversal.visit(new FallbackModeVisitor(), mth);
			} catch (DecodeException e) {
				LOG.error("Error reload instructions in fallback mode:", e);
				code.startLine("// Can't loadFile method instructions: " + e.getMessage());
				return;
			}
		}
		InsnNode[] insnArr = mth.getInstructions();
		if (insnArr == null) {
			code.startLine("// Can't load method instructions.");
			return;
		}
		if (mth.getThisArg() != null) {
			code.startLine(nameGen.useArg(mth.getThisArg())).add(" = this;");
		}
		addFallbackInsns(code, mth, insnArr, true);
	}

	public static void addFallbackInsns(CodeWriter code, MethodNode mth, InsnNode[] insnArr, boolean addLabels) {
		InsnGen insnGen = new InsnGen(getFallbackMethodGen(mth), true);
		for (InsnNode insn : insnArr) {
			if (insn == null) {
				continue;
			}
			if (addLabels) {
				if (insn.contains(AType.JUMP)
						|| insn.contains(AType.EXC_HANDLER)) {
					code.decIndent();
					code.startLine(getLabelName(insn.getOffset()) + ":");
					code.incIndent();
				}
			}
			try {
				if (insnGen.makeInsn(insn, code)) {
					CatchAttr catchAttr = insn.get(AType.CATCH_BLOCK);
					if (catchAttr != null) {
						code.add("\t " + catchAttr);
					}
				}
			} catch (CodegenException e) {
				LOG.debug("Error generate fallback instruction: ", e.getCause());
				code.startLine("// error: " + insn);
			}
		}
	}

	/**
	 * Return fallback variant of method codegen
	 */
	static MethodGen getFallbackMethodGen(MethodNode mth) {
		ClassGen clsGen = new ClassGen(mth.getParentClass(), null, true);
		return new MethodGen(clsGen, mth);
	}

	public static String getLabelName(int offset) {
		return "L_" + InsnUtils.formatOffset(offset);
	}

}
