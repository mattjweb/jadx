package jadx.gui.treemodel;

import jadx.api.JavaClass;
import jadx.api.JavaField;
import jadx.api.JavaMethod;
import jadx.core.dex.info.AccessInfo;
import jadx.gui.utils.NLS;
import jadx.gui.utils.Utils;

import javax.swing.Icon;
import javax.swing.ImageIcon;

public class JClass extends JNode {
	private static final long serialVersionUID = -1239986875244097177L;

	private static final ImageIcon ICON_CLASS = Utils.openIcon("class_obj");
	private static final ImageIcon ICON_CLASS_DEFAULT = Utils.openIcon("class_default_obj");
	private static final ImageIcon ICON_CLASS_PRIVATE = Utils.openIcon("innerclass_private_obj");
	private static final ImageIcon ICON_CLASS_PROTECTED = Utils.openIcon("innerclass_protected_obj");
	private static final ImageIcon ICON_INTERFACE = Utils.openIcon("int_obj");
	private static final ImageIcon ICON_ENUM = Utils.openIcon("enum_obj");
	private static final ImageIcon ICON_ANNOTATION = Utils.openIcon("annotation_obj");

	private final JavaClass cls;
	private final JClass jParent;
	private boolean loaded;

	public JClass(JavaClass cls) {
		this.cls = cls;
		this.jParent = null;
		this.loaded = false;
	}

	public JClass(JavaClass cls, JClass parent) {
		this.cls = cls;
		this.jParent = parent;
		this.loaded = true;
	}

	public JavaClass getCls() {
		return cls;
	}

	public synchronized void load() {
		if (!loaded) {
			cls.decompile();
			loaded = true;
			updateChilds();
		}
	}

	@Override
	public synchronized void updateChilds() {
		removeAllChildren();
		if (!loaded) {
			add(new TextNode(NLS.str("tree.loading")));
		} else {
			for (JavaClass javaClass : cls.getInnerClasses()) {
				JClass child = new JClass(javaClass, this);
				add(child);
				child.updateChilds();
			}
			for (JavaField f : cls.getFields()) {
				add(new JField(f, this));
			}
			for (JavaMethod m : cls.getMethods()) {
				add(new JMethod(m, this));
			}
		}
	}

	public String getCode() {
		return cls.getCode();
	}

	@Override
	public Icon getIcon() {
		AccessInfo accessInfo = cls.getAccessInfo();

		if (accessInfo.isEnum()) {
			return ICON_ENUM;
		} else if (accessInfo.isAnnotation()) {
			return ICON_ANNOTATION;
		} else if (accessInfo.isInterface()) {
			return ICON_INTERFACE;
		} else if (accessInfo.isProtected()) {
			return ICON_CLASS_PROTECTED;
		} else if (accessInfo.isPrivate()) {
			return ICON_CLASS_PRIVATE;
		} else if (accessInfo.isPublic()) {
			return ICON_CLASS;
		} else {
			return ICON_CLASS_DEFAULT;
		}
	}

	@Override
	public JClass getJParent() {
		return jParent;
	}

	@Override
	public JClass getRootClass() {
		if (jParent == null) {
			return this;
		}
		return jParent.getRootClass();
	}

	@Override
	public int getLine() {
		return cls.getDecompiledLine();
	}

	@Override
	public String toString() {
		return cls.getShortName();
	}
}
