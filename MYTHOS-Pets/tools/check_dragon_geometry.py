"""Count nondegenerate scale and two-sided membrane triangles by source parameters."""
import pathlib
import re

source = pathlib.Path("src/main/java/com/ksp/mythospets/DragonMeshModel.java").read_text()
def array(name):
    match = re.search(r"int\[\]\s+" + name + r"\s*=\s*\{([^}]+)\}", source)
    assert match, name
    return [int(x) for x in match.group(1).split(",")]

rows, cols, steps = map(array, ("SCALE_ROWS", "SCALE_COLUMNS", "MEMBRANE_STEPS"))
sections = [(int(r), int(c)) for r, c in re.findall(r"p\.scales\(anatomy\.\w+,(\d+),(\d+)\);", source)]
assert len(sections) == 4
# Wing rendering iterates both sides. Each patch has steps^2 triangles per side.
patches = len(re.findall(r"p\.membrane\(", source)) * 2
assert patches == 32
budgets = []
for lod in range(4):
    count = sum((r * rows[lod] // 96) * (c * cols[lod] // 64) for r, c in sections)
    budget = 18 * count + 2 * patches * steps[lod] ** 2
    budgets.append(budget)
    print(f"LOD {lod}: {count:,} scales; {budget:,} nominal nondegenerate detail triangles")
assert 250_000 <= budgets[0] <= 275_000, budgets
assert all(a > b for a, b in zip(budgets, budgets[1:])), budgets
# This is a source-derived budget, not a GPU counter or performance benchmark.
