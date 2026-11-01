import os
import unittest

from commands import markdown, parse, suggestions

HERE = os.path.dirname(os.path.abspath(__file__))


class ParseTest(unittest.TestCase):
    def test_apps(self):
        self.assertEqual(parse("Open Netflix"), ("app", "com.netflix.ninja"))
        self.assertEqual(parse("launch hot star"), ("app", "in.startv.hotstar"))
        self.assertEqual(parse("you tube"), ("app", "com.google.android.youtube.tv"))
        self.assertEqual(parse("play spotify"), ("app", "com.spotify.tv.android"))
        self.assertEqual(parse("open prime video"), ("app", "com.amazon.amazonvideo.livingroom"))

    def test_keys_and_repeat(self):
        self.assertEqual(parse("volume up"), ("key", 24, 1))
        self.assertEqual(parse("volume up 5"), ("key", 24, 5))
        self.assertEqual(parse("turn it down three times"), ("key", 25, 3))
        self.assertEqual(parse("pause"), ("key", 127, 1))
        self.assertEqual(parse("play"), ("key", 126, 1))
        self.assertEqual(parse("next"), ("key", 87, 1))
        self.assertEqual(parse("down 50"), ("key", 20, 20))   # capped

    def test_polite_wording(self):
        for said in ("home", "go to home", "take me home", "press the home button", "Home."):
            self.assertEqual(parse(said), ("key", 3, 1), said)
        self.assertEqual(parse("go back"), ("key", 4, 1))
        self.assertEqual(parse("go to the back"), ("key", 4, 1))

    def test_close(self):
        self.assertEqual(parse("close netflix"), ("close", "com.netflix.ninja"))
        self.assertEqual(parse("Quit You Tube"), ("close", "com.google.android.youtube.tv"))
        self.assertEqual(parse("stop spotify"), ("close", "com.spotify.tv.android"))
        self.assertEqual(parse("close app"), ("closefront",))
        self.assertEqual(parse("close this app"), ("closefront",))
        self.assertEqual(parse("exit"), ("closefront",))
        self.assertEqual(parse("stop"), ("key", 127, 1))   # alone it is still Pause
        self.assertIsNone(parse("close the door"))

    def test_volume_level_and_type(self):
        self.assertEqual(parse("volume 30"), ("volume", 30))
        self.assertIsNone(parse("set volume to twenty"))   # only digits and one..ten
        self.assertEqual(parse("set volume to 7"), ("volume", 7))
        self.assertEqual(parse("type Hello World"), ("type", "Hello World"))

    def test_every_suggestion_is_understood(self):
        for text in suggestions():
            self.assertIsNotNone(parse(text), text)

    def test_docs_are_current(self):
        with open(os.path.join(HERE, "COMMANDS.md"), encoding="utf-8", newline="") as f:
            self.assertEqual(f.read(), markdown(), "run: python commands.py > COMMANDS.md")

    def test_nonsense(self):
        self.assertIsNone(parse("what is the capital of france"))
        self.assertIsNone(parse(""))
        self.assertIsNone(parse("what is up"))
        self.assertIsNone(parse("i want to go back to my old house"))


if __name__ == "__main__":
    unittest.main()
