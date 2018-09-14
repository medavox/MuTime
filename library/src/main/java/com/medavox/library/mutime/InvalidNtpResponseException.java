package com.medavox.library.mutime;

import java.io.IOException;
import java.util.Locale;

/**Thrown when an NTP server sends back an invalid response.
 */
public class InvalidNtpResponseException
      extends IOException {

    public final String property;
    public final float expectedValue;
    public final float actualValue;

    InvalidNtpResponseException(String detailMessage) {
        super(detailMessage);

        this.property = "n/a";
        this.expectedValue = 0F;
        this.actualValue = 0F;
    }

/***Constructs a new instance.
 * @param message An informative message that api users can use to know what went wrong.
 *                should contain {@link #property}, {@link #expectedValue} and
 *                {@link #actualValue} as format specifiers (in that order)
 * @param property  property that caused the invalid NTP response*/
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
