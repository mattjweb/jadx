package jadx.tests.internal.trycatch;

import jadx.api.InternalJadxTest;
import jadx.core.dex.nodes.ClassNode;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

public class TestTryCatch2 extends InternalJadxTest {

	public static class TestCls {
		private final static Object obj = new Object();

		private static boolean test() {
			try {
				synchronized (obj) {
					obj.wait(5);
				}
				return true;
			} catch (InterruptedException e) {
				return false;
			}
		}
	}

	@Test
	public void test() {
		ClassNode cls = getClassNode(TestCls.class);
		String code = cls.getCode().toString();
		System.out.println(code);

		assertThat(code, containsString("try {"));
		assertThat(code, containsString("synchronized (obj) {"));
		assertThat(code, containsString("obj.wait(5);"));
		assertThat(code, containsString("return true;"));
		assertThat(code, containsString("} catch (InterruptedException e) {"));
		assertThat(code, containsString("return false;"));
	}
}
