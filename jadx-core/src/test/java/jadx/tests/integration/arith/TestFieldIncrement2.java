package jadx.tests.integration.arith;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

import jadx.NotYetImplemented;
import jadx.core.dex.nodes.ClassNode;
import jadx.tests.api.IntegrationTest;

public class TestFieldIncrement2 extends IntegrationTest {

	public static class TestCls {
		private static class A {
			int f = 5;
		}

		public A a;

		public void test1(int n) {
			this.a.f = this.a.f + n;
		}

		public void test2(int n) {
			this.a.f *= n;
		}
	}

	@Test
	public void test() {
		ClassNode cls = getClassNode(TestCls.class);
		String code = cls.getCode().toString();

		assertThat(code, containsString("this.a.f += n;"));
		assertThat(code, containsString("a.f *= n;"));
	}

	@Test
	@NotYetImplemented
	public void test2() {
		ClassNode cls = getClassNode(TestCls.class);
		String code = cls.getCode().toString();

		assertThat(code, containsString("this.a.f *= n;"));
	}
}
