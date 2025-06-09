from PIL import Image
import math
import numpy
import os


# red channel have two times less brightness than green so make it more intensive
def make_red(image_arr, x1, y1, x2, y2, a = 25.0):

	assert a > 1.0

	for x in range(x1, x2):
		for y in range(y1, y2):
			r, g, b, a = image_arr[x, y]

			rg = math.log(float(r) + a, float(g) + a)
			rb = math.log(float(r) + a, float(b) + a)
			c = (rg + rb) / 2.0

			if c >= 1.0 and r != 0:
				r = numpy.uint8(numpy.clip(float(r) * c, 0.0, 255.0))
				image_arr[x, y] = r, g, b, a


def make_black(image_arr, x1, y1, x2, y2):

	for x in range(x1, x2):
		for y in range(y1, y2):
			r, g, b, a = image_arr[x, y]

			if r > g:
				image_arr[x, y] = b, b, b, a


def make_green(image_arr, x1, y1, x2, y2):

	for x in range(x1, x2):
		for y in range(y1, y2):
			r, g, b, a = image_arr[x, y]

			if r > g:
				image_arr[x, y] = g, r, b, a


def make_white(image_arr, x1, y1, x2, y2):

	for x in range(x1, x2):
		for y in range(y1, y2):
			r, g, b, a = image_arr[x, y]

			if r > g:
				image_arr[x, y] = r, r, r, a


# terminator_960.png
# regions = [(240, 360, 440, 500), (512, 360, 712, 500)]

# terminator_240.png
regions = [(60, 90, 110, 125), (128, 90, 178, 125)]

filepath = "terminator.png"
filepath1, filepath2 = os.path.splitext(filepath)

image = Image.open(filepath).convert("RGBA")

image_red = image.copy()
image_black = image.copy()
image_green = image.copy()
image_white = image.copy()

if len(regions) == 0:
	make_red(image_red.load(), 0, 0, *image_red.load().shape)
else:
	for i in range(len(regions)):
		make_red(image_red.load(), *regions[i])

image_red.save(filepath1 + "_red" + filepath2)

if len(regions) == 0:
	make_black(image_black.load(), 0, 0, *image_black.load().shape)
else:
	for i in range(len(regions)):
		make_black(image_black.load(), *regions[i])

image_black.save(filepath1 + "_black" + filepath2)

if len(regions) == 0:
	make_green(image_green.load(), 0, 0, *image_green.load().shape)
else:
	for i in range(len(regions)):
		make_green(image_green.load(), *regions[i])

image_green.save(filepath1 + "_green" + filepath2)

if len(regions) == 0:
	make_white(image_white.load(), 0, 0, *image_white.load().shape)
else:
	for i in range(len(regions)):
		make_white(image_white.load(), *regions[i])

image_white.save(filepath1 + "_white" + filepath2)