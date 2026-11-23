"""编排器参考文献收集逻辑：semantic_search / get_literature / read_section / summarize 全覆盖。"""
from orchestrator import Orchestrator


def collect(*calls):
    refs = []
    for tool_name, args, result in calls:
        Orchestrator._collect_reference(tool_name, args, result, refs)
    return refs


def test_semantic_search_extracts_results():
    refs = collect(
        ("semantic_search", {}, '{"results": [{"literatureId": 3, "title": "Paper C"}, {"literatureId": 7, "title": "Paper G"}]}'),
    )
    assert refs == [{"id": 3, "title": "Paper C"}, {"id": 7, "title": "Paper G"}]


def test_search_results_dedup_by_id():
    refs = collect(
        ("semantic_search", {}, '{"results": [{"literatureId": 3, "title": "Paper C"}]}'),
        ("semantic_search", {}, '{"results": [{"literatureId": 3, "title": "Paper C"}, {"literatureId": 5, "title": "Paper E"}]}'),
    )
    assert refs == [{"id": 3, "title": "Paper C"}, {"id": 5, "title": "Paper E"}]


def test_read_section_then_get_literature_keeps_first_title():
    # read_section 无标题占位（id 已记录）；同 id 的 get_literature 不覆盖已有标题
    refs = collect(
        ("semantic_search", {}, '{"results": [{"literatureId": 7, "title": "Paper G"}]}'),
        ("read_section", {"literature_id": 7}, "某段正文..."),
        ("get_literature", {}, '{"id": 7, "title": "Paper G Full"}'),
    )
    assert refs == [{"id": 7, "title": "Paper G"}]


def test_read_section_new_id_title_backfilled_later():
    refs = collect(
        ("read_section", {"literature_id": 12}, "x"),
        ("get_literature", {}, '{"id": 12, "title": "Paper L"}'),
    )
    assert refs == [{"id": 12, "title": "Paper L"}]


def test_get_literature_error_result_skipped():
    refs = collect(
        ("get_literature", {}, '{"error": "文献不存在或未公开: 9"}'),
    )
    assert refs == []


def test_summarize_uses_args_literature_id():
    refs = collect(
        ("summarize", {"literature_id": "3"}, "总结文本"),
    )
    assert refs == [{"id": 3, "title": None}]


def test_invalid_literature_id_ignored():
    refs = collect(
        ("read_section", {"literature_id": "abc"}, "x"),
        ("read_section", {}, "x"),
    )
    assert refs == []


def test_non_json_tool_result_ignored():
    refs = collect(
        ("semantic_search", {}, "not json at all"),
        ("get_literature", {}, "broken"),
    )
    assert refs == []
