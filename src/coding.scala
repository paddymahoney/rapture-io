/**************************************************************************************************
Rapture I/O Library
Version 0.8.1

The primary distribution site is

  http://www.propensive.com/

Copyright 2010-2013 Propensive Ltd.

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
compliance with the License. You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is
distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied. See the License for the specific language governing permissions and limitations under the
License.
***************************************************************************************************/

package rapture.implementation
import rapture._

trait Codecs extends Encodings with ExceptionHandling {

  /** Standard Base64 codec. */
  object Base64 extends Base64Codec

  /** Base64-URL codec, a URL-safe version of the Base64 codec which works in the same way as the
    * standard codec, except uses the characters - and _ instead of + and / respectively. */
  object Base64Url extends Base64Codec(char62 = '-', char63 = '_')

  /** RFC2045 base-64 codec, based on http://migbase64.sourceforge.net/. */
  class Base64Codec(val char62: Char = '+', val char63: Char = '/', val padChar: Char = '=',
      val lineBreaks: Boolean = false, val endPadding: Boolean = false) {
    
    private val alphabet =
      ("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"+char62+char63).toCharArray
    
    private lazy val decodabet = {
      val x = new Array[Int](256)
      for(i <- 0 until alphabet.length) x(alphabet(i)) = i
      x
    }


    /** Convenience method for encoding a string using the Base64 codec. */
    def encode(in: String)(implicit encoding: Encoding): String =
      new String(encode(in.getBytes(encoding.name)))

    /** Non-RFC-compliant encoder. */

    /** Encoder. The RFC requires that line breaks be added every 76 chars, and
      * that the data be padded to a multiple of 4 chars, but we do these things
      * optionally. */
    def encode(in: Array[Byte]): String = {
      
      var inLen = in.length
      
      if(inLen == 0) "" else {
        val evenLen = (inLen/3)*3
        val outDataLen = if(endPadding) ((inLen - 1)/3 + 1) << 2 else ((inLen << 2) + 2 )/3
        val outLen = if(lineBreaks) outDataLen + (outDataLen - 1)/76 << 1 else outDataLen
        val out = new Array[Char](outLen)

        var inPos = 0
        var outPos = 0
        var blockCount = 0
        
        while(inPos < evenLen) {
          val block = (in(inPos) & 0xFF) << 16 | (in(inPos + 1) & 0xFF) << 8 | (in(inPos + 2) &
              0xFF)
          
          inPos += 3
          
          out(outPos) = alphabet((block >>> 18) & 0x3F)
          out(outPos + 1) = alphabet((block >>> 12) & 0x3F)
          out(outPos + 2) = alphabet((block >>> 6) & 0x3F)
          out(outPos + 3) = alphabet(block & 0x3F)
          
          outPos += 4

          if(lineBreaks) {
            
            blockCount += 1
            
            if(blockCount == 19 && outPos < outLen - 2) {
              out(outPos) = '\r'
              out(outPos + 1) = '\n'
              outPos += 2
              blockCount = 0
            }
          }
        }

        val left = inLen - evenLen
        
        if(left > 0) {
          
          val block =
            ((in(evenLen) & 0xFF) << 10) | (if(left == 2) (in(inLen - 1) & 0xFF) << 2 else 0)
          
          out(outPos) = alphabet(block >>> 12)
          out(outPos + 1) = alphabet((block >>> 6) & 0x3F)
          
          if(left == 2) out(outPos + 2) = alphabet(block & 0x3F)
          else if(endPadding) out(outPos + 2) = padChar
          
          if(endPadding) out(outPos + 3) = padChar
        }
        
        new String(out)
      }
    }

    /** Decoder. Supports all the variants produced by the encoder above, but
      * does not tolerate any other illegal characters, including line breaks at
      * positions other than 76-char boundaries, in which case the result will
      * be garbage. */
    def decode(data: String)(implicit eh: ExceptionHandler): eh.![Exception, Array[Byte]] =
      eh.except {
        val in = data.toCharArray()

        val inLen = in.length
        
        if(inLen == 0) new Array[Byte](0) else {
          
          val padding = if(in(inLen - 1) == padChar) (if(in(inLen - 2) == padChar) 2 else 1) else 0

          // FIXME: This doesn't seem to accommodate different kinds of linebreak
          val lineBreaks = if(inLen > 76) (if(in(76) == '\r') inLen/78 else 0) << 1 else 0
          val outLen = ((inLen - lineBreaks) * 6 >> 3) - padding
          val out = new Array[Byte](outLen)

          var inPos = 0
          var outPos = 0
          var blockCount = 0
          
          val evenLen = (outLen/3)*3
          
          while(outPos < evenLen) {
            
            val block = decodabet(in(inPos)) << 18 | decodabet(in(inPos + 1)) << 12 |
                decodabet(in(inPos + 2)) << 6 | decodabet(in(inPos + 3))
            
            inPos += 4

            out(outPos) = (block >> 16).toByte
            out(outPos + 1) = (block >> 8).toByte
            out(outPos + 2) = block.toByte
            outPos += 3

            if(lineBreaks > 0) {
              
              blockCount += 1
              
              if(blockCount == 19) {
                inPos += 2
                blockCount = 0
              }
            }
          }

          if(outPos < outLen) {
            val block = decodabet(in(inPos)) << 18 | decodabet(in(inPos + 1)) << 12 |
                (if(inPos + 2 < inLen - padding) decodabet(in(inPos + 2)) << 6 else 0)

            out(outPos) = (block >> 16).toByte
            
            if(outPos + 1 < outLen) out(outPos + 1) = (block >> 8).toByte
          }

          out
        }
      }

    /** Encodes the input byte array as an array of characters */
    def apply(in: Array[Byte]): String = encode(in)
    
    /** Decodes the input character array into an array of bytes. */
    def unapply(in: String): Option[Array[Byte]] = Some(decode(in))
  }

  object Hex {
    def encode(a: Array[Byte]): String =
      new String(a flatMap { n => Array((n & 255) >> 4 & 15, n & 15) } map { _ + 48 } map { i =>
        (if(i > 57) i + 39 else i).toChar })

    def decode(s: String)(implicit eh: ExceptionHandler): eh.![Exception, Array[Byte]] = eh.except {
      (if(s.length%2 == 0) s else "0"+s).to[Array].grouped(2).to[Array] map { case Array(l, r) =>
        (((l - 48)%39 << 4) + (r - 48)%39).toByte
      }
    }
  }

}
