"""Check the API after importing the generated demo data (at least 8 recipes)."""

import argparse
import json
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import urlopen


def get(base, path, expected=200, **params):
    url = base.rstrip("/") + path
    if params:
        url += "?" + urlencode(params)
    try:
        with urlopen(url, timeout=10) as response:
            assert response.status == expected, (url, response.status)
            return json.load(response)
    except HTTPError as error:
        if error.code != expected:
            raise
        return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    args = parser.parse_args()
    base = args.base
    assert get(base, "/api/health")["status"] == "ok"

    first = get(base, "/api/recipes", limit=2)
    second = get(base, "/api/recipes", limit=2, after=first["nextAfter"])
    assert [item["id"] for item in first["items"]] == [1, 2]
    assert [item["id"] for item in second["items"]] == [3, 4]

    search = get(base, "/api/recipes", q="番茄", ingredient="鸡蛋", category="面食", maxMinutes=17, limit=1)
    assert [item["id"] for item in search["items"]] == [1]
    shorter = get(base, "/api/recipes", q="番茄", ingredient="鸡蛋", category="面食", maxMinutes=16, limit=1)
    assert all(item["id"] != 1 for item in shorter["items"])
    empty = get(base, "/api/recipes", q="找不到这道菜")
    assert empty["items"] == [] and "nextAfter" not in empty

    detail = get(base, "/api/recipes/1")
    assert detail["recipe"]["name"] == "番茄鸡蛋面"
    assert [row["name"] for row in detail["ingredients"]] == ["番茄", "鸡蛋", "面条"]
    assert detail["categories"] == ["面食"] and detail["keywords"] == ["家常"]
    assert detail["nutrition"]["calories"] == 201
    assert get(base, "/api/recipes/1/reviews")[0]["text"] == '说明清晰，有 "家常" 风味。'
    get(base, "/api/recipes/-1", expected=404)
    get(base, "/api/recipes/-1/reviews", expected=404)
    get(base, "/api/recipes", expected=400, limit=101)
    get(base, "/api/recipes", expected=400, maxMinutes=-1)
    print("API smoke checks passed: database, pagination, filters, detail, reviews, 400 and 404")


if __name__ == "__main__":
    main()
