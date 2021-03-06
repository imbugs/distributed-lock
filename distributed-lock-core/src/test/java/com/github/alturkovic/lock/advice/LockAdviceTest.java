/*
 * Copyright (c)  2017 Alen Turković <alturkovic@gmail.com>
 *
 * Permission to use, copy, modify, and distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package com.github.alturkovic.lock.advice;

import com.github.alturkovic.lock.Lock;
import com.github.alturkovic.lock.Locked;
import com.github.alturkovic.lock.advice.dummy.CustomLocked;
import com.github.alturkovic.lock.advice.dummy.DummyLock;
import com.github.alturkovic.lock.advice.dummy.DummyLocked;
import com.github.alturkovic.lock.converter.IntervalConverter;
import com.github.alturkovic.lock.exception.DistributedLockException;
import com.github.alturkovic.lock.key.SpelKeyGenerator;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class LockAdviceTest {

  private LockedInterface locked;

  @Mock
  private Lock lock;

  @Before
  public void init() {
    final AspectJProxyFactory proxyFactory = new AspectJProxyFactory(new LockedService());
    final Map<Class<? extends Lock>, Lock> lockMap = new HashMap<>();
    lockMap.put(Lock.class, lock);
    lockMap.put(DummyLock.class, lock);
    final IntervalConverter intervalConverter = new IntervalConverter(new DefaultListableBeanFactory());
    proxyFactory.addAspect(new LockAdvice(intervalConverter, new SpelKeyGenerator(), lockMap));
    locked = proxyFactory.getProxy();
  }

  @Test
  public void shouldAcquireDefaultLockAndCallMethod() {
    when(lock.acquire(anyListOf(String.class), any(), anyLong())).thenReturn("abc");
    when(lock.release(eq(Collections.singletonList("lock:com.github.alturkovic.lock.advice.LockAdviceTest.LockedService.tryWithDefaultLock")), eq("abc"), any()))
        .thenReturn(true);

    locked.tryWithDefaultLock();

    verify(lock, atLeastOnce())
        .release(eq(Collections.singletonList("lock:com.github.alturkovic.lock.advice.LockAdviceTest.LockedService.tryWithDefaultLock")), eq("abc"), any());
  }

  @Test
  public void shouldAcquireNamedLockAndCallMethod() {
    when(lock.acquire(anyListOf(String.class), any(), anyLong())).thenReturn("abc");
    when(lock.release(eq(Collections.singletonList("lock:test")), eq("abc"), any())).thenReturn(true);

    locked.tryWithNamedLock();

    verify(lock, atLeastOnce()).release(eq(Collections.singletonList("lock:test")), eq("abc"), any());
  }

  @Test(expected = DistributedLockException.class)
  public void shouldNotAcquireNamedLockAndThrowException() {
    when(lock.acquire(anyListOf(String.class), any(), anyLong())).thenReturn(null);

    locked.tryWithNamedLock();
  }

  @Test(expected = DistributedLockException.class)
  public void shouldFailWithUnregisteredLockType() {
    locked.tryWithUnregisteredLock();
  }

  @Test
  public void shouldLockWithLockAlias() {
    when(lock.acquire(anyListOf(String.class), any(), anyLong())).thenReturn("abc");
    when(lock.release(eq(Collections.singletonList("lock:aliased")), eq("abc"), any())).thenReturn(true);

    locked.tryWithLockAlias();

    verify(lock, atLeastOnce()).release(eq(Collections.singletonList("lock:aliased")), eq("abc"), any());
  }

  interface LockedInterface {
    void tryWithDefaultLock();

    void tryWithNamedLock();

    void tryWithLockAlias();

    void tryWithUnregisteredLock();
  }

  static class LockedService implements LockedInterface {

    @Locked
    public void tryWithDefaultLock() {
    }

    @Locked(expression = "'test'")
    public void tryWithNamedLock() {
    }

    @Override
    @DummyLocked(expression = "'aliased'")
    @SuppressWarnings("unchecked") // added @Override and @SuppressWarnings to make sure merging works fine with other annotations
    public void tryWithLockAlias() {
    }

    @CustomLocked
    public void tryWithUnregisteredLock() {
    }
  }
}