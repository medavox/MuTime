package com.instacart.library.truetime;

import java.io.IOException;
import java.util.Locale;

public class InvalidNtpResponseException
      extends IOException {

    public final String property;
    public final float expectedValue;
    public final float actualValue;

    InvalidNtpResponseException(String detailMessage) {
        super(detailMessage);

        this.property = "na";
        this.expectedValue = 0F;
        this.actualValue = 0F;
    }

  /**
   *
   * @param message An informative message that api users can use to know what went wrong.
   *                should contain {@link #property}, {@link #expectedValue} and
   *                {@link #actualValue} as format specifiers (in that order)
   * @param property  property that caused the invalid NTP response
   */
  InvalidNtpResponseException(String message,
                              String property,
                              float actualValue,
                              float expectedValue) {

      super(String.format(Locale.getDefault(),
          message,
          property,
          actualValue,
          expectedValue));

      this.property = property;
      this.actualValue = actualValue;
      this.expectedValue = expectedValue;
   }
}
