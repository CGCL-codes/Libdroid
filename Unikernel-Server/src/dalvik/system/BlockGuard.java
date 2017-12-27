/*
 * Daneel - Dalvik to Java bytecode compiler
 * Copyright (C) 2011  IcedRobot team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This file is subject to the "Classpath" exception:
 *
 * Linking this library statically or dynamically with other modules is
 * making a combined work based on this library.  Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * As a special exception, the copyright holders of this library give you
 * permission to link this library with independent modules to produce an
 * executable, regardless of the license terms of these independent
 * modules, and to copy and distribute the resulting executable under terms
 * of your choice, provided that you also meet, for each linked independent
 * module, the terms and conditions of the license of that module.  An
 * independent module is a module which is not derived from or based on
 * this library.  If you modify this library, you may extend this exception
 * to your version of the library, but you are not obligated to do so.  If
 * you do not wish to do so, delete this exception statement from your
 * version.
 */

package dalvik.system;

/**
 * Compatibility wrapper for Dalvik VM interface. The block guard allows
 * applications to specify what operations each thread is allowed to perform.
 * The block guard has to be implemented on a best-effort basis, so we can
 * safely ignore it and just provide an empty stub.
 */
public class BlockGuard {

    public static final int DISALLOW_DISK_WRITE = 1;
    public static final int DISALLOW_DISK_READ = 2;
    public static final int DISALLOW_NETWORK = 4;
    public static final int PASS_RESTRICTIONS_VIA_RPC = 8;
    public static final int PENALTY_LOG = 16;
    public static final int PENALTY_DIALOG = 32;
    public static final int PENALTY_DEATH = 64;

    /**
     * The interface for a block guard policy specifying what operations a
     * thread is allowed to perform.
     */
    public static interface Policy {
        int getPolicyMask();
        void onNetwork();
        void onReadFromDisk();
        void onWriteToDisk();
    };

    /**
     * The initial block guard policy that each thread starts out with, if not
     * specified explicitly.
     */
    public static final Policy LAX_POLICY = new Policy() {
        public int getPolicyMask() { return 0; }
        public void onNetwork() {}
        public void onReadFromDisk() {}
        public void onWriteToDisk() {}
    };

    /**
     * Gets the block guard policy for the current thread.
     * @return The block guard policy.
     */
    public static Policy getThreadPolicy() {
        return threadPolicy.get();
    }

    /**
     * Sets the block guard policy for the current thread.
     * @param policy The block guard policy.
     */
    public static void setThreadPolicy(Policy policy) {
        threadPolicy.set(policy);
    }

    private static final ThreadLocal<Policy> threadPolicy;
    static {
        threadPolicy = new ThreadLocal<Policy>() {
            @Override
            protected Policy initialValue() {
                return LAX_POLICY;
            }
        };
    }

    private BlockGuard() {
        // No instances of this class.
    }

    /**
     * The exception used to indicate violations of block guard policies.
     */
    public static class BlockGuardPolicyException extends RuntimeException {

        /**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		private int policyState;
        private int policyViolated;

        public BlockGuardPolicyException(int policyState, int policyViolated) {
            this.policyState = policyState;
            this.policyViolated = policyViolated;
        }

        @Override
        public String getMessage() {
            return super.getMessage();
        }

        public int getPolicy() {
            return policyState;
        }

        public int getPolicyViolation() {
            return policyViolated;
        }
    }
}
