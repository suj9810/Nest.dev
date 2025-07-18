package caffeine.nest_dev.domain.category.service;

import caffeine.nest_dev.common.dto.PagingResponse;
import caffeine.nest_dev.common.enums.ErrorCode;
import caffeine.nest_dev.common.exception.BaseException;
import caffeine.nest_dev.domain.category.dto.request.CategoryRequestDto;
import caffeine.nest_dev.domain.category.dto.response.CategoryResponseDto;
import caffeine.nest_dev.domain.category.entity.Category;
import caffeine.nest_dev.domain.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    @CacheEvict(value = "categoriesPage", allEntries = true)
    public CategoryResponseDto creatCategory(CategoryRequestDto categoryRequestDto) {

        categoryRepository.findByName(categoryRequestDto.getName())
                .ifPresent(category -> {
                    throw new BaseException(ErrorCode.CATEGORY_ALREADY_EXISTS);
                });

        Category category = categoryRequestDto.toEntity();
        Category save = categoryRepository.save(category);

        return CategoryResponseDto.of(save);

    }

    @Cacheable(value = "categoriesPage",
            key = "#pageable.pageNumber + '-' + #pageable.pageSize + '-' + #pageable.sort.isUnsorted()")
    public PagingResponse<CategoryResponseDto> getCategories(Pageable pageable) {

        Page<Category> categories = categoryRepository.findAll(pageable);

        Page<CategoryResponseDto> map = categories.map(CategoryResponseDto::of);

        return PagingResponse.from(map);
    }

    @Transactional
    @CacheEvict(value = "categoriesPage", allEntries = true)
    public CategoryResponseDto updateCategory(Long categoryId,
            CategoryRequestDto categoryRequestDto) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BaseException(ErrorCode.CATEGORY_NOT_FOUND));

        String newName = categoryRequestDto.getName();

        String name = category.getName();

        // 같은 상태로 변경 요청시 에러
        if (name.equals(newName)) {
            throw new BaseException(ErrorCode.ALREADY_SAME_CATEGORY_NAME);
        }
        category.update(newName);

        return CategoryResponseDto.of(category);
    }

    @Transactional
    @CacheEvict(value = "categoriesPage", allEntries = true)
    public void deleteCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BaseException(ErrorCode.CATEGORY_NOT_FOUND));

        category.softDelete();
    }
}
