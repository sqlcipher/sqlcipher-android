/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.zetetic.database;

/**
 * This exception is thrown when a CursorWindow couldn't be allocated,
 * most probably due to memory not being available.
 *
 * @hide
 */
public class CursorWindowAllocationException extends RuntimeException {
   public CursorWindowAllocationException(String description) {
      super(description);
   }
}

