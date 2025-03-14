#include <string.h>
#include <math.h>
#include <vector>
#include <cstdlib>

#include "drivers/st7789/st7789.hpp"
#include "libraries/pico_display/pico_display.hpp"
#include "libraries/pico_graphics/pico_graphics.hpp"
#include "drivers/rgbled/rgbled.hpp"

#include "global_revoke_display.h"

// QRCODE
#include <stdbool.h>
#include <stddef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "qrcodegen.h"

using namespace pimoroni;

ST7789 st7789(PicoDisplay::WIDTH, PicoDisplay::HEIGHT, ROTATE_0, false, get_spi_pins(BG_SPI_FRONT));
PicoGraphics_PenRGB332 graphics(st7789.width, st7789.height, nullptr);

RGBLED led(PicoDisplay::LED_R, PicoDisplay::LED_G, PicoDisplay::LED_B);

extern "C" int draw_text(const char* displayText) {
  // set the backlight to a value between 0 and 255
  // the backlight is driven via PWM and is gamma corrected by our
  // library to give a gorgeous linear brightness range.
  st7789.set_backlight(100);

  // make the led glow green
  // parameters are red, green, blue all between 0 and 255
  // these are also gamma corrected
  led.set_rgb(255, 128, 128); //RED
  led.set_brightness(10);
  // led.set_rgb(179, 255, 179); //GREEN
  // led.set_rgb(255, 255, 51); //YELLOW

  // set the colour of the pen
  // parameters are red, green, blue all between 0 and 255
  graphics.set_pen(30, 40, 50);

  // fill the screen with the current pen colour
  graphics.clear();

  // draw a box to put some text in
  // graphics.set_pen(10, 20, 30);
  // Rect text_rect(10, 10, 150, 150);
  // graphics.rectangle(text_rect);

  // write some text inside the box with 10 pixels of margin
  // automatically word wrapping
  // text_rect.deflate(10);

  // graphics.set_pen(110, 120, 130);
  graphics.set_pen(255, 255, 255); //White color
  // graphics.text(displayText, Point(text_rect.x, text_rect.y), text_rect.w);
  graphics.text(displayText, Point(5, 5),true);

  // now we've done our drawing let's update the screen
  st7789.update(&graphics);

  return 0;
}

extern "C" int draw_qrcode(const char* displayText) {
  // set the backlight to a value between 0 and 255
  // the backlight is driven via PWM and is gamma corrected by our
  // library to give a gorgeous linear brightness range.
  st7789.set_backlight(100);

  // make the led glow green
  // parameters are red, green, blue all between 0 and 255
  // these are also gamma corrected
  led.set_rgb(255, 128, 128); //RED
  led.set_brightness(10);
  // led.set_rgb(179, 255, 179); //GREEN
  // led.set_rgb(255, 255, 51); //YELLOW

  // set the colour of the pen
  // parameters are red, green, blue all between 0 and 255
  graphics.set_pen(30, 40, 50);

  // fill the screen with the current pen colour
  graphics.clear();

  // Write the title
  graphics.set_pen(230, 0, 0); //RED
  // graphics.set_pen(255, 255, 255); //White
  graphics.text("Global Revoke Key", Point(125, 10),true);
  //

  //Prepare and Draw QR Code
  const char *text = displayText;                // User-supplied text
  enum qrcodegen_Ecc errCorLvl = qrcodegen_Ecc_LOW;  // Error correction level

  // Make and print the QR Code symbol
  uint8_t qrcode[qrcodegen_BUFFER_LEN_MAX];
  uint8_t tempBuffer[qrcodegen_BUFFER_LEN_MAX];
  bool ok = qrcodegen_encodeText(text, tempBuffer, qrcode, errCorLvl,
    qrcodegen_VERSION_MIN, qrcodegen_VERSION_MAX, qrcodegen_Mask_AUTO, true);
  if (ok){
    int size = qrcodegen_getSize(qrcode);
    int border = 4;
    for (int y = -border; y < size + border; y++) {
      for (int x = -border; x < size + border; x++) {
        fputs((qrcodegen_getModule(qrcode, x, y) ? ".." : "  "), stdout);
        if(qrcodegen_getModule(qrcode, x, y))
        {
          // graphics.set_pen(255, 255, 255); //White
          graphics.set_pen(230, 230, 0); //Yellow
        }else
        {
          graphics.set_pen(0, 0, 0);
        }
        // Draw using pixels
        // Point point(x,y);
        // graphics.set_pixel(point);
        //
        // Draw using rectangle
        int module_size = int(150 / size); // 100px is size of qrcode.
        Rect rectQRCode(5 + x * module_size, 5 + y * module_size, module_size, module_size);
        graphics.rectangle(rectQRCode);
      }
      fputs("\n", stdout);
    }
    fputs("\n", stdout);
  }

  // now we've done our drawing let's update the screen
  st7789.update(&graphics);

  return 0;
}